/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62;

import be.goudvuur.base.bbor62.ifaces.BitReader;
import be.goudvuur.base.bbor62.ifaces.BitWriter;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * My implementation of a streaming base62 encoder/decoder.
 * This actually works with any alphabet/charset (only tested with base62 and base64).
 * It works by encoding as much data as possible into blocks of 4 chars (even trying to squeeze in more bits if the remaining space allows it).
 * The last block is decoded using statistical analysis on the possible final states of the encoder (see ref spreadsheet).
 *
 * Created by bram on Nov 26, 2024
 */
public class BaseXStream
{
    public interface Config
    {
        /**
         * The alphabet of this base
         */
        String dict();

        /**
         * How big are our blocks?
         */
        int charsPerBlock();

        /**
         * enable/disable using the unused space to squeeze in more bits if possible
         */
        boolean enableBitSqueezing();

        // ---------- BELOW CAN BE CALCULATED FROM ABOVE ----------

        /**
         * The reversed alphabet, indexed by char index
         */
        Map<Character, Integer> dictRev();

        /**
         * the "base" in our baseXX implementation
         */
        int radix();

        /**
         * How many bits fit into a single block, guaranteed? (regardless of bit squeezing)
         */
        int bitsPerBlock();

        /**
         * When looking at bits per block, what's the maximum number that can be encoded into a single block?
         */
        int maxBlockValue();

        /**
         * (For bit squeezing) When looking at chars per block, what's the maximum number that can be encoded into a single block?
         * The difference between "max block capacity" and "max block value" is the "free space" in a block where we can try to squeeze in more data.
         */
        int maxBlockCapacity();
    }

    public static final Config DEFAULT_CONFIG = new Config()
    {
        private static final String DICT = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        private static final Map<Character, Integer> DICT_REV = new HashMap<>();
        static {
            for (int i = 0; i < DICT.length(); i++) {
                DICT_REV.put(DICT.charAt(i), i);
            }
        }

        // the "base" in our baseXX implementation
        private final int RADIX = this.dict().length();

        // How many bits can we encode with 1 char? -> log2(RADIX)
        // To know the practical bits, this should be ceiled (since log2(62) ≈ 5.95, we need 6 bits)
        private final double BITS_PER_CHAR = Math.log(this.RADIX) / Math.log(2);

        // We need to floor so that the bits in a block never overflow (the remainder here is actually the "unused space" we'll try to squeeze bits in)
        // always make sure this value, plus the possible squeezed bits
        // can't overflow the available bits in the type of the buffer (eg. int = 32 bits, so this+squeezed must be <= 32)
        private final int BITS_PER_BLOCK = (int) Math.floor(this.BITS_PER_CHAR * this.charsPerBlock());

        // Maximum value in 23 bits
        private final int MAX_BLOCK_VALUE = (int) (Math.pow(2, this.BITS_PER_BLOCK) - 1);

        // 62^4 = 14,776,336
        private final int MAX_BLOCK_CAPACITY = (int) Math.pow(this.RADIX, this.charsPerBlock());

        @Override
        public String dict()
        {
            return DICT;
        }
        /**
         * From block analysis:
         * 23 bits: 2^23 = 8,388,608
         * needs 4 chars (62^4 = 14,776,336)
         * efficiency: 23/4 = 5.75 bits/char
         * The pattern is: the best efficiency comes just before we need an additional character.
         * These points are where:
         * 2^n is just under 62^m
         * For m=2: n=11 (2048 vs 3844)
         * For m=3: n=17 (131,072 vs 238,328)
         * For m=4: n=23 (8,388,608 vs 14,776,336)
         * Best efficiency points:
         * 29 bits → 5 chars = 5.8 bits/char  (916,132,832 values)
         * 23 bits → 4 chars = 5.75 bits/char (14,776,336 values)
         * 17 bits → 3 chars = 5.67 bits/char (238,328 values)
         * 11 bits → 2 chars = 5.5 bits/char  (3,844 values)
         * -> Let's use blocksize of 4 as a good tradeoff between size and efficiency
         * Also, it still fits in a 4-byte java integer we use as buffer
         */
        @Override
        public int charsPerBlock()
        {
            return 4;
        }
        @Override
        public boolean enableBitSqueezing()
        {
            return true;
        }
        @Override
        public Map<Character, Integer> dictRev()
        {
            return DICT_REV;
        }
        @Override
        public int radix()
        {
            return RADIX;
        }
        @Override
        public int bitsPerBlock()
        {
            return BITS_PER_BLOCK;
        }
        @Override
        public int maxBlockValue()
        {
            return MAX_BLOCK_VALUE;
        }
        @Override
        public int maxBlockCapacity()
        {
            return MAX_BLOCK_CAPACITY;
        }
    };

    // let's make it clear when we're calculating with bytes throughout the code
    public static final int BITS_PER_BYTE = 8;

    public static class Encoder implements BitWriter
    {
        private final Consumer<String> consumer;
        private final Config config;
        // sync this type with the bits check in write()
        private int buffer;
        private int bitsInBuffer;
        private int byteModulo;

        public Encoder(Consumer<String> consumer, Config config)
        {
            this.consumer = consumer;
            this.config = config;
            this.buffer = 0;
            this.bitsInBuffer = 0;
            this.byteModulo = 0;
        }

        @Override
        public void write(int value, int numBits)
        {
            if (numBits <= 0 || numBits > Integer.SIZE) {
                throw new IllegalArgumentException("Number of bits must be between 1 and " + Integer.SIZE);
            }
            // note that the L is needed or the value overflows
            if (value >= (1L << numBits)) {
                throw new IllegalArgumentException("Value is too large for the specified number of bits");
            }

            // this is basically the same loop as in ByteStream,
            // except for the bit squeezing and result.append()
            while (numBits > 0) {
                // the bits left in the current block
                int bitsAvailable = this.config.bitsPerBlock() - this.bitsInBuffer;
                int bitsToWrite = Math.min(numBits, bitsAvailable);
                int remainingBits = numBits - bitsToWrite;

                int mask = (1 << bitsToWrite) - 1;
                int val = (value >>> remainingBits) & mask;

                this.buffer = (this.buffer << bitsToWrite) | val;
                this.bitsInBuffer += bitsToWrite;
                this.byteModulo = (this.byteModulo + bitsToWrite) % BaseXStream.BITS_PER_BYTE;

                // because of the logic above, this will never grow larger
                if (this.bitsInBuffer == this.config.bitsPerBlock()) {

                    // check if we can squeeze in more bits
                    if (this.config.enableBitSqueezing()) {
                        // note that this means bit squeezing will never activate when writes are aligned with the block sizes
                        // or when bitsNotToEncode happens to be zero. For that, we always need to wait until next bits are
                        // available in the buffer. But since we only use this sparingly to push 24 bits into 4 base62 chars,
                        // and we use weird block sizes of 23, we should have an extra bit most of the time
                        while (remainingBits > 0) {
                            // what value do we get if we add one extra bit?
                            int extraBitVal = (value >>> (remainingBits - 1)) & 1;
                            int tryBuffer = (this.buffer << 1) | extraBitVal;
                            // this means the try value is in the "unused" range (the range we can't reach with 2^23, but can with 62^4)
                            if (tryBuffer > this.config.maxBlockValue() && tryBuffer < this.config.maxBlockCapacity()) {
                                this.buffer = tryBuffer;
                                this.bitsInBuffer++;
                                this.byteModulo = (this.byteModulo + 1) % BaseXStream.BITS_PER_BYTE;
                                remainingBits--;
                            }
                            else {
                                // stop as soon as we can't squeeze more bits
                                break;
                            }
                        }
                    }

                    this.consumer.accept(this.encode(this.buffer, false));

                    this.buffer = 0;
                    this.bitsInBuffer = 0;
                }

                numBits = remainingBits;
            }
        }
        @Override
        public void flush()
        {
            // Handle remaining bits
            // Note that this can't grow larger than BITS_PER_BLOCK, see write()
            if (this.bitsInBuffer > 0) {
                // option 1: right-pad the last chunk with zeros if not a complete char
                //this.buffer = this.buffer << (BITS_PER_BLOCK - this.bitsInBuffer);
                //this.bitsInBuffer = BITS_PER_BLOCK;

                // option 2: encode the length of the remaining bits as the first char
                //this.consumer.accept(CHARSET.charAt(this.bitsInBuffer));

                // option 3: encode the length of the remaining bits into the unused encoding space
                // not okay: this will turn a 1 char to a 4 char
                //this.buffer = this.buffer + ((1 << BITS_PER_BLOCK) + this.bitsInBuffer);

                // option 4: do everything in the decoder if we require the stream is byte aligned
                // note that we can check the modulo here, because all write() calls have been made
                if (this.byteModulo == 0) {
                    // the buffer can still contain bits from a previous run, make sure to mask them out
                    int mask = (1 << this.bitsInBuffer) - 1;
                    int valueToEncode = this.buffer & mask;

                    // Note that we don't need to squeeze in more bits because there's no next block

                    String lastCode = this.encode(valueToEncode, true);
                    this.consumer.accept(lastCode);
                }
                else {
                    throw new IllegalStateException("We can't unambiguously decode this stream because it's not byte aligned");
                }
            }

            this.buffer = 0;
            this.bitsInBuffer = 0;
        }

        private String encode(int valueToEncode, boolean finalBlock)
        {
            StringBuilder retVal = new StringBuilder();

            // make sure to always write it out once, otherwise we can't encode zero
            do {
                int remainder = valueToEncode % this.config.radix();
                valueToEncode = Math.floorDiv(valueToEncode, this.config.radix());
                retVal.insert(0, this.config.dict().charAt(remainder));
            } while (valueToEncode != 0);

            // left-pad with zero if we don't have a full block,
            // except for the final block
            if (!finalBlock) {
                while (retVal.length() < this.config.charsPerBlock()) {
                    retVal.insert(0, this.config.dict().charAt(0));
                }
            }
            // if we reach the final block and its value got compressed so much that we can't unambiguously tell the decoder
            // how many bits were left in the last block, we need to prepend with zeros until we can
            // Eg. bitsInBuffer = 13, buffer = 0000000000001, value = 1 will otherwise get encoded as "1"
            //     but the decoder can never know there were actually 13 bits encoded because it looks at the charLength of the block
            else {
                // calculate the maximum number of bits we can encode with the number of chars in the last block
                // (also see spreadsheet at https://docs.google.com/spreadsheets/d/1j1v2bHS79YVRj7kF7NR6UnMgjAxyD9-OhPnYV-1oFKs/edit?gid=0#gid=0)
                // Note that the while loop statement is the combination of this (same code as in decoder):
                //int maxValue = (int) (Math.pow(Base62Encoder.RADIX, retVal.length()) - 1);
                //int maxBits = BaseXStream.log2ceil(maxValue);
                while (BaseXStream.log2ceil((int) (Math.pow(this.config.radix(), retVal.length()) - 1)) < this.bitsInBuffer) {
                    retVal.insert(0, this.config.dict().charAt(0));
                }
            }

            return retVal.toString();
        }
    }

    public static class Decoder implements BitReader
    {
        private final Config config;
        private int buffer;
        private int bitsInBuffer;
        private int byteModulo;
        private StringBuilder currentBlock;
        private int blockVal;
        private int blockPos;
        private Map<Map.Entry<Integer, Integer>, Integer> finalBlockLut;

        // these are to make this stream bit-read compatible (instead of reading it char by char)
        private CharSequence input;
        private int inputPos;
        private ByteStream readBuffer;

        public Decoder(CharSequence input, Config config)
        {
            this.config = config;
            this.buffer = 0;
            this.bitsInBuffer = 0;
            this.byteModulo = 0;
            this.currentBlock = new StringBuilder();
            this.blockVal = 0;
            this.blockPos = this.config.charsPerBlock() - 1;
            this.finalBlockLut = buildFinalBlockLut();

            this.inputPos = 0;
            this.readBuffer = new ByteStream();
            this.input = input;
        }

        @Override
        public int read(int numBits)
        {
            return this.assertBits(numBits).read(numBits);
        }
        @Override
        public boolean hasNext(int numBits)
        {
            // watch out: this is the inverse of the while loop check in assertBits(), but I guess it's not 100% correct
            // because we don't know how many bits are left in the next base62 chars.
            // Leaving it like this for now, because it's valid when used like this:
            // while (decoder.hasNext(8)) {
            //     outputStream.write((byte) decoder.read(8));
            // }
            return this.readBuffer.hasNext(numBits) || this.inputPos < this.input.length();
        }

        private ByteStream assertBits(int numBits)
        {
            while (!this.readBuffer.hasNext(numBits) && this.inputPos < this.input.length()) {
                boolean lastChar = this.inputPos == this.input.length() - 1;
                this.readChar(this.input.charAt(this.inputPos++), lastChar);
            }

            return this.readBuffer;
        }

        private void readChar(char c, boolean lastChar)
        {
            Integer index = this.config.dictRev().get(c);
            if (index == null) {
                throw new IllegalArgumentException("Invalid base62 character: " + c);
            }

            // decode the character and increment with the current block number value
            this.blockVal = (int) (this.blockVal + (index * Math.pow(this.config.radix(), this.blockPos--)));
            // keep track of the current non-complete block for the remainder tracking
            this.currentBlock.append(c);

            // we have a full block decoded in blockVal
            if (this.blockPos < 0) {

                // note that it's possible we encounter the last block here (if it's perfectly byte aligned)
                int bitsInBlock = lastChar ? this.getLastBlockBitLength() : this.config.bitsPerBlock();
                this.decodeBlock(bitsInBlock);

                this.currentBlock.setLength(0);
                this.blockVal = 0;
                this.blockPos = this.config.charsPerBlock() - 1;
            }

            // if this is the last char, handle the last block and close the stream
            if (lastChar) {

                if (this.currentBlock.length() > 0) {

                    // Note: here, we know that this.bitsInBuffer < 8 (see decodeBlock())

                    // recompute the final blockval without assuming it should be complete
                    this.blockVal = 0;
                    this.blockPos = this.currentBlock.length() - 1;
                    for (int i = 0; i < currentBlock.length(); i++) {
                        this.blockVal = (int) (this.blockVal + (this.config.dictRev().get(currentBlock.charAt(i)) * Math.pow(this.config.radix(), this.blockPos--)));
                    }

                    int lastBlockBits = this.getLastBlockBitLength();
                    this.decodeBlock(lastBlockBits);
                }

                // note that this can be solved by building a bitstream instead of a bytestream,
                // but in reality we won't need it much I think
                if (this.bitsInBuffer > 0) {
                    throw new IllegalStateException("Bitstream is not byte aligned, can't return byte buffer from this value");
                }

                this.buffer = 0;
                this.bitsInBuffer = 0;
                this.byteModulo = 0;
            }
        }

        private void decodeBlock(int numBits)
        {
            // Check if the encoder pushed extra bits into the unused space of this block
            // Note that this is detected transparently so it's only up to the encoder to activate/deactivate it
            int extraBits = 0;
            // we don't need to test < Base62Encoder.MAX_BLOCK_CAPACITY because it needs to fit into 1 block
            while (this.blockVal >>> extraBits > this.config.maxBlockValue()) {
                numBits++;
                extraBits++;
            }

            int mask = (1 << numBits) - 1;
            this.buffer = (this.buffer << numBits) | (this.blockVal & mask);
            this.bitsInBuffer += numBits;
            this.byteModulo = (this.byteModulo + numBits) % BaseXStream.BITS_PER_BYTE;

            // write out the buffer byte per byte
            while (this.bitsInBuffer >= BaseXStream.BITS_PER_BYTE) {
                int bitsNotToDecode = this.bitsInBuffer - BaseXStream.BITS_PER_BYTE;
                int valueToDecode = this.buffer >>> bitsNotToDecode;

                this.readBuffer.write((byte) valueToDecode, BaseXStream.BITS_PER_BYTE);

                int m = (1 << bitsNotToDecode) - 1;
                this.buffer &= m;
                this.bitsInBuffer = bitsNotToDecode;
            }
        }

        private int getLastBlockBitLength()
        {
            // this is an edge case: when the last block if full, and we have modulo zero,
            // we actually don't have a last (incomplete) block because the bits aligned perfectly
            if (this.byteModulo == 0 && this.currentBlock.length() == this.config.charsPerBlock()) {
                return this.config.bitsPerBlock();
            }
            else {
                Map.Entry<Integer, Integer> entry = new AbstractMap.SimpleImmutableEntry<>(this.byteModulo, this.currentBlock.length());
                if (this.finalBlockLut.containsKey(entry)) {
                    return this.finalBlockLut.get(entry);
                }
                else {
                    throw new IllegalArgumentException("Invalid last block combination: startPos=" + this.byteModulo + ", numChars=" + this.currentBlock.length());
                }
            }
        }

        /**
         * Let's start by analyzing the last block handling in the encoder:
         * - when closing the stream, the encoder detects there are bits left that don't form a full block, so lastBlockBits < BITS_PER_BLOCK
         * - the encoder encodes those final bits without left-padding to generate the shortest last block possible
         * - the problem is that the decoder needs to know the number of bits used to encode that last block
         *   For instance 1 char (62 values) could represent anything from 1 to 5 bits
         *   e.g., "A" could be "1" (1 bit) or "00001" (5 bits)
         *   The decoder only knows N is in range of the bits required for a single char [1-6 bits]
         * - but we require that the entire stream is byte aligned, so totalBitLength % 8 == 0
         * - but the next-to-last full 23-bit block might have ended in the middle of a byte:
         *
         * [23 bits][23 bits][23 bits][remaining bits + final bits...]
         *                             ^
         *                             |
         *                             These bits are all part of our final block that must close the stream with modulo 8 == 0
         *
         * For any N bits < 23:
         * If these N bits generate M base-62 chars when encoded,
         * then N must have been large enough to require M chars.
         * And N couldn't have been large enough to need M+1 chars
         * (or encoder would have output M+1 chars).
         *
         * So when decoder sees the final block, it knows N must be in range:
         *   - Large enough to need M chars
         *   - Small enough to not need M+1 chars
         *
         * Code below generates a LUT for valid end states that translate (byteModulo, numChars) to a single bitLength.
         * Note that because we use byteModulo, this transparently supports bit squeezing
         *
         * This was validated in a LUT for base62,
         * see https://docs.google.com/spreadsheets/d/1j1v2bHS79YVRj7kF7NR6UnMgjAxyD9-OhPnYV-1oFKs/edit?gid=0#gid=0
         */
        private Map<Map.Entry<Integer, Integer>, Integer> buildFinalBlockLut()
        {
            Map<Map.Entry<Integer, Integer>, Integer> retVal = new HashMap<>();

            // For each possible number of base62 chars
            for (int numChars = 1; numChars <= this.config.charsPerBlock(); numChars++) {

                int minValue = (int) (Math.pow(this.config.radix(), numChars - 1));
                // the minimum number of bits needed to encode minValue (note min 1 since 62^0 == 1)
                int minBits = Math.max(BaseXStream.log2ceil(minValue), 1);

                int maxValue = (int) (Math.pow(this.config.radix(), numChars) - 1);
                // note that we can never exceed BITS_PER_BLOCK, even when the maxValue allows it (eg. for 62^4, cap to 23, not 24)
                int maxBits = Math.min(BaseXStream.log2ceil(maxValue), this.config.bitsPerBlock());

                // For each starting modulo position (0-7) and actual number of chars seen (1-4)
                // find the valid bit length that reaches byte alignment
                for (int modulo = 0; modulo < BaseXStream.BITS_PER_BYTE; modulo++) {
                    for (int bitLength = minBits; bitLength <= maxBits; bitLength++) {
                        if ((modulo + bitLength) % BaseXStream.BITS_PER_BYTE == 0) {
                            Map.Entry<Integer, Integer> startState = new AbstractMap.SimpleImmutableEntry<>(modulo, numChars);
                            if (!retVal.containsKey(startState)) {
                                retVal.put(startState, bitLength);
                            }
                            else {
                                throw new IllegalStateException("This configuration of block size + charset cannot guarantee a unique final block bit length!!!");
                            }
                        }
                    }
                }
            }

            return retVal;
        }
    }

    /**
     * See Guava's IntMath.log2() with rounding mode CEILING
     * or https://graphics.stanford.edu/%7Eseander/bithacks.html#IntegerLogObvious
     */
    private static int log2ceil(int x)
    {
        return Integer.SIZE - Integer.numberOfLeadingZeros(x - 1);
    }

}
