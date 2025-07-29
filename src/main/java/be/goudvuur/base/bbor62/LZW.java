/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62;

import be.goudvuur.base.bbor62.ifaces.BitReader;
import be.goudvuur.base.bbor62.ifaces.BitWriter;
import be.goudvuur.base.bbor62.ifaces.Compressor;

import java.util.HashMap;
import java.util.Map;

/**
 * My implementation of the Lempel-Ziv-Welch (LZW) compression algorithm.
 * The LZW algorithm is an elegant and widely-used data compression technique that works by
 * identifying and encoding repeating sequences of data.
 * <p>
 * Created by bram on Oct 30, 2024
 */
public class LZW implements Compressor
{
    // this can be enabled during debugging to print out all operations
    private static final boolean ENABLE_DEBUG = false;

    // calculate these here so we can referece them without requiring they are at index 0 & 1 in the dict
    private static final int ASCII_ESCAPE_IDX = 0;    // Used for other ASCII chars
    private static final String ASCII_ESCAPE_VAL = String.valueOf((char) ASCII_ESCAPE_IDX);
    private static final int UNICODE_ESCAPE_IDX = 1;  // Used for Unicode
    private static final String UNICODE_ESCAPE_VAL = String.valueOf((char) UNICODE_ESCAPE_IDX);

    public interface Config
    {
        /**
         * this toggles if the dictionary should get new, dynamic new chars or ngrams,
         * or if it should just consider the static dict and build nothing on top
         */
        boolean enableDynamicDict();

        /**
         * Maximum 10-bit code (1024) feels like a good value (keeping in mind this needs to run client side too)
         */
        int maxDictSize();

        /**
         * this toggles how the dict acts when it's full:
         * - if disabled, it will refuse any more entries (so all new chars will get serialized)
         * - if enabled, it will reset itself (clear the dynamic part) so the encoder can start over
         * this saves memory and offers the possibility for better encoding if the text entropy shifted
         */
        boolean enableDictReset();

        /**
         * When enabled, the bitstream is padded to always form a full byte at the end.
         * The decompressor is adjusted to handle this.
         */
        boolean byteAlignMode();

        /**
         * The initial static dict <String, Integer> that at least needs ASCII_ESCAPE_VAL (index 0) and UNICODE_ESCAPE_VAL (index 1)
         */
        Map<Object, Object> staticDict();

        /**
         * The same as getStaticDict() but reversed as <Integer, String>
         */
        Map<Object, Object> staticDictRev();
    };

    public static final Config DEFAULT_CONFIG = new Config()
    {
        // Careful trial-error selected ngrams that seem to hit the sweet spot
        private static final String[] INITIAL_DICT = {

                        // two first entries are used by ASCII_ESCAPE and UNICODE_ESCAPE
                        // make sure their values correspond with their index, the encoder counts on this!
                        // ASCII_ESCAPE
                        ASCII_ESCAPE_VAL, // 0
                        // UNICODE_ESCAPE
                        UNICODE_ESCAPE_VAL, // 1
                        // = dict size 2

                        // 10 digits in order
                        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                        // +10 = dict size 12

                        // very common punctuation
                        " ", ".", ",",
                        // +3 = dict size 15

                        // Here are the 26 most common lowercase characters in Western texts, from most to least frequent (b) slightly edited
                        // This analysis accounts for frequency patterns across major Western languages including
                        // English, Spanish, French, German, Italian, and Portuguese.
                        "e", "a", "r", "i", "o", "t", "n", "s", "l", "c",
                        // +10 = dict size 25
                        "u", "d", "p", "m", "h", "g", "b", "f", "v", "k",
                        // +10 = dict size 35
                        "w", "j",
                        // +2 = dict size 37
                        // 2x omitting these allow us to stay under 64 (but it doesn't help that much, so enabling
                        "q", "x", "y", "z",
                        // +4 = dict size 41

                        // Here are the 26 most frequently used capital letters in Western texts, from most to least frequent (b) slightly edited
                        // (including English, French, Spanish, German, Italian, etc.)
                        // Note that some languages may use additional capital letters with diacritical marks (like Á, É, Ñ)
                        // which aren't included in this basic Latin alphabet list.
                        "A", "M", "S", "C", "P", "D", "B", "R", "L", "T",
                        // +10 = dict size 51
                        "E", "N", "H", "G", "F", "W", "I", "J", "K", "O",
                        // +10 = dict size 61
                        "V", "U",
                        // +2 = dict size 63
                        // 2x omitting these allow us to stay under 64 (but it doesn't help that much, so enabling
                        "Q", "X", "Y", "Z",
                        // +4 = dict size 67

                        // 10 very common bigrams in western languages (helps to compress quite a bit)
                        "th", "en", "er", "in", "es", "on", "an", "re", "st", "le",
                        // +10 = dict size 77

                        // total size of 77 (7 bits) offers us 128-77=51 places before the dict index will grow 1 bit
                        // --> larger json files will generate far bigger dicts, but this should be good enough for small ones
                        // --------------------------------------

                        // common punctuation (disabled because not so common in short json payloads)
                        //"?", "!", ";", ":", "-", "(", ")",
                        // +7 = dict size 84

                        // 16 extra bigrams in western languages (debatable)
                        //"ed", "nd", "to", "or", "at", "ng", "al", "it", "se", "ar", "nt", "te", "co", "ra", "he", "de",
                        // +16 = dict size 100

                        // 10 common trigrams (doesn't add much more compression while wasting dict space)
                        //"the", "ing", "and", "ion", "ent", "der", "ers", "est", "que", "ver",
                        // +10 = dict size 110

                        // 4 extra 1 char word trigrams (debatable)
                        //" a ", " e ", " i ", " o "
                        // +4= dict size 114

                        // json reserved words (these are serialized away by cbor anyway and don't occur in normal text much)
                        //"null", "true", "false"
                        // +3 = dict size 117
        };

        // Static dictionaries for encoding and decoding
        // String -> Integer
        private static final Map<Object, Object> STATIC_DICT = new HashMap<>();
        // Integer -> String
        private static final Map<Object, Object> STATIC_DICT_REV = new HashMap<>();  // 0-63 plus escapes
        static {
            for (int i = 0; i < INITIAL_DICT.length; i++) {
                String c = INITIAL_DICT[i];
                if (!STATIC_DICT.containsKey(c)) {
                    STATIC_DICT.put(c, i);
                    STATIC_DICT_REV.put(i, c);
                }
                else {
                    throw new IllegalArgumentException(String.format("Duplicate dict character detected '%s'", c));
                }
            }
        }

        @Override
        public boolean enableDynamicDict()
        {
            return true;
        }
        @Override
        public int maxDictSize()
        {
            return (int) (Math.pow(2, 10) - 1);
        }
        @Override
        public boolean enableDictReset()
        {
            return true;
        }
        @Override
        public boolean byteAlignMode()
        {
            return true;
        }
        @Override
        public Map<Object, Object> staticDict()
        {
            return STATIC_DICT;
        }
        @Override
        public Map<Object, Object> staticDictRev()
        {
            return STATIC_DICT_REV;
        }
    };

    /**
     * Little string wrapper to also store it was new or not
     */
    private static class Token
    {
        String value = null;
        boolean isNew = false;
    }

    /**
     * Context helper class to pass values by reference
     */
    private static class Ctx
    {
        final FlexDict dict;
        int byteAlignVal = 0;

        public Ctx(FlexDict dict)
        {
            this.dict = dict;
        }
    }

    private final Config config;
    // we need to make the dictionaries class fields so multiple compress()/decompress() calls can reuse the
    // previous dictionary. This improves json compressing quite a bit because the dicts are reused for every field/string
    // in the json file.
    // If you don't want this, or need to reset, just create a new LZW instance
    private FlexDict encodeDict;
    private FlexDict decodeDict;

    public LZW(Config config)
    {
        this.config = config;
        // Note: dicts will be lazy loaded
        this.encodeDict = null;
        this.decodeDict = null;
    }

    @Override
    public void compress(String input, BitWriter output)
    {
        // Handle empty input
        if (input == null || input.isEmpty()) {
            throw new IllegalStateException("Please provide input");
        }

        if (this.encodeDict == null) {
            this.encodeDict = new FlexDict(this.config.staticDict(), this.config.enableDynamicDict(), this.config.maxDictSize());
        }
        Ctx ctx = new Ctx(this.encodeDict);

        // note: don't reset the dict automatically because this way,
        // we can compress all strings in a json file where one can build on the dict
        // of the other string in the json file, probably reusing a lot of pre-encountered token

        String last = String.valueOf(input.charAt(0));
        for (int i = 1; i < input.length(); i++) {

            String current = String.valueOf(input.charAt(i));

            // on first run, this will just be the first char
            // note that it can still be in the dict, though
            String lastPlusCurrent = last + current;

            // If the last plus current sequence exists in dictionary,
            // we can save space by referring to it and search for even longer sequences.
            if (ctx.dict.hasKey(lastPlusCurrent)) {
                last = lastPlusCurrent;
            }
            // If sequence is not in dictionary, we'll write out the last and add it to the dict.
            else {

                // we write a sequence out as soon as we can't append to it anymore so it forms a dict entry
                // note that because of this, only the last char (or only char) in 'last' can be new (but doesn't have to be new)
                boolean isNew = this.write(output, ctx, last);

                // this is a good place to check the dict size because we can sync the decoder with it
                this.checkReset(ctx.dict);

                if (isNew) {
                    // the first time we write a new character, we'll also add it to the dict,
                    // so all next codes can be sent saving at least one byte (if the dictionary doesn't grow very big)
                    ctx.dict.add(last, ctx.dict.size());
                }

                // always add to the general dict
                // note that it's not in there, we checked that above
                // also note that we are in the if() block, so we don't always blindly add the first character
                ctx.dict.add(lastPlusCurrent, ctx.dict.size());

                // we tried to look up last + next char, but that failed,
                // so process the next char individually
                last = current;
            }
        }

        // Don't forget to output the code for the last sequence
        boolean isNew = this.write(output, ctx, last);

        // don't forget to do these too because sometimes we want to reuse the dict for multiple encodings

        this.checkReset(ctx.dict);

        if (isNew) {
            ctx.dict.add(last, ctx.dict.size());
        }

        if (this.config.byteAlignMode() && ctx.byteAlignVal > 0) {

            // As long as the decoder knows the total byte length of the stream, we don't need any extra dict chars to mark the end:
            // - if bitsNeededForSeq > bitsUntilFullByte:
            //   we can't append a full sequence without overflowing to the next byte,
            //   so we can just pad with anything because the decoder knows how to handle this by
            //   checking the remaining bits in the last byte and discard them (eat them up) if they're not enough to create a sequence.
            //   So we might as well use UNICODE_ESCAPE_IDX to make the implementation simpler.
            // - if bitsNeededForSeq <= bitsUntilFullByte:
            //   here, we have room in the current byte to put one or more sequences.
            //   We can use any escape char with zeros appended because the remaining byte to fill can never hold
            //   ASCII_ESCAPE_IDX+8bit or UNICODE_ESCAPE_IDX+16bit and the decoder and easily detect this.
            int bitsUntilFullByte = 8 - ctx.byteAlignVal;
            int bitsNeededForSeq = this.bitsNeeded(ctx.dict.size() - 1);
            int bitsToWrite = Math.min(bitsNeededForSeq, bitsUntilFullByte);
            output.write(ASCII_ESCAPE_IDX, bitsToWrite);
            if (ENABLE_DEBUG) Logger.log("\t WRITE\t\t" + "pad" + "\t\t(code ASC" + ", " + bitsNeededForSeq + " bits, size " + ctx.dict.size() + ")");
            bitsUntilFullByte -= bitsToWrite;
            // fill the rest of the byte with zeros if we have space left
            if (bitsUntilFullByte > 0) {
                output.write(0, bitsUntilFullByte);
                if (ENABLE_DEBUG) Logger.log("\t WRITE\t\t" + "pad" + "\t\t(code 000" + ", " + bitsUntilFullByte + " bits, size " + ctx.dict.size() + ")");
            }
            ctx.byteAlignVal = 0;
        }
    }

    @Override
    public String decompress(BitReader input)
    {
        StringBuilder retVal = new StringBuilder();

        if (this.decodeDict == null) {
            this.decodeDict = new FlexDict(this.config.staticDictRev(), this.config.enableDynamicDict(), this.config.maxDictSize());
        }
        Ctx ctx = new Ctx(this.decodeDict);

        // An introduction
        // Look at the encoder: the only block that 'emits' output is the
        // else() block where three things happen:
        // - write the code
        // - add to dict if new
        // - add lastPlusCurrent to dict
        // So the decoding loop can be considered as an iteration of those else() blocks over and over.
        //
        // Keep in mind that in the encoder, 'current' is always a single char, while in the decoder, it might be a sequence

        // The first code is required to be in the dictionary (but can be a marker + new).
        // Otherwise, we don't have a uniform way of knowing how many bits it took to write the first code.
        // The max bits needed for the first code is the number of bits needed for the last entry of the dict.
        int nextIndex = ctx.dict.size() - 1;
        String last = null;
        Token current;
        while ((current = this.read(input, ctx, nextIndex, last)) != null) {

            // we might as well append to retVal straight away
            // instead of appending 'last' in the else() block and one more time after the loop
            retVal.append(current.value);

            // if this is the first run, we need to skip it because we can't concat yet
            // this is analogous to how the encoder starts at index 1 instead of 0
            if (last != null) {

                // this adds lastPlusCurrent to the dict, but note that this is actually
                // the lastPlusCurrent of the previous encoder iteration (where we wrote 'last', see encoder),
                // and where 'current' was still a single character (in the encoder).
                ctx.dict.add(ctx.dict.size(), last + current.value.charAt(0));
            }

            // here, we 'caught up' with the encoder loop (just after write()),
            // but we still need to reset if full or add current to the dict if it was new

            this.checkReset(ctx.dict);

            if (current.isNew) {
                ctx.dict.add(ctx.dict.size(), current.value);
            }

            last = current.value;

            // After the encoder wrote() the first char, it adds lastPlusCurrent to the dict.
            // But we skipped that above because during the first iteration, we don't know the next char yet.
            // So we'll create a 'hole' here (the + 1) and read the next char in the following iteration.
            // The following iteration will fill it before we add a possible new char to the dict.
            // (here, we reverse the order in which the dict.add happens compared to the encoder)
            // Note that this only happens when dynamic dict is enabled, otherwise the dict doesn't grow, and we don't need to do anything
            if (this.config.enableDynamicDict()) {
                nextIndex = (ctx.dict.size() - 1) + 1;
            }
        }

        return retVal.toString();
    }

    private boolean write(BitWriter output, Ctx ctx, String value)
    {
        boolean isNew = false;

        Integer code = (Integer) ctx.dict.get(value);

        // how many bits do we need to represent dictionary entries up until now?
        // --> the bits needed for the maximum dict code we could have written until now
        int bitsNeeded = bitsNeeded(ctx.dict.size() - 1);

        // the value is in the dict
        if (code != null) {
            if (ENABLE_DEBUG) {
                Logger.log("\t WRITE\t\t" + value + "\t\t(code " + (code == ASCII_ESCAPE_IDX || code == UNICODE_ESCAPE_IDX ? ASCII_ESCAPE_IDX + "+8" : code) + ", " + bitsNeeded + " bits, size " +
                            ctx.dict.size() + ")");
            }
            // this means the input string contains the character used for ASCII_ESCAPE or UNICODE_ESCAPE in the dict
            // because when we lookup the code for the supplied value, it's an escape code
            if (code == ASCII_ESCAPE_IDX || code == UNICODE_ESCAPE_IDX) {
                // in this case, write() will write out
                // ASCII_ESCAPE + ASCII_ESCAPE
                // or
                // ASCII_ESCAPE + UNICODE_ESCAPE
                // so we can safely switch to ascii mode to save a byte
                // !!! but make sure:
                //  - the index of the escape tokens is < 256
                //  - their index correspond to their ascii value
                output.write(ASCII_ESCAPE_IDX, bitsNeeded);
                // output the code we're escaping using the full 8 bits
                output.write(code, 8);
            }
            else {
                output.write(code, bitsNeeded);
            }
        }
        else {

            if (value.length() == 1) {

                // note: don't make this an int, or the String.valueOf() below fails
                char c = value.charAt(0);

                if (ENABLE_DEBUG) {
                    Logger.log("\t WRITE\t\t" + value + "(" + c + ")" + "\t(code " + (c < 256 ? ASCII_ESCAPE_IDX + "+8" : UNICODE_ESCAPE_IDX + "+16") + ", " + bitsNeeded + " bits, size " +
                                ctx.dict.size() + ")");
                }

                if (c < 256) {
                    output.write(ASCII_ESCAPE_IDX, bitsNeeded);
                    output.write(c, 8);
                }
                // Note that in Java, strings are represented as UTF-16 characters,
                // so in the event of eg. emoji characters, they will be encoded correctly,
                // because every character is encoded as either:
                // - 2 bytes (one 16-bit unit) for BMP
                // - 4 bytes (surrogate pair) for anything above BMP
                // - (Cannot represent more due to surrogate pair design)
                // So String methods, such as .length(), return the number of UTF-16 char values,
                // not the number of actual Unicode characters and that's why this works.
                else {
                    output.write(UNICODE_ESCAPE_IDX, bitsNeeded);
                    output.write(c, 16);
                }

                isNew = true;
            }
            else {
                throw new IllegalStateException("This shouldn't happen, all new values > 1 should be in the dict; " + value);
            }
        }

        // note that all extra bits we write above are either 8 or 16, so we only need to consider bitsNeeded
        ctx.byteAlignVal = (ctx.byteAlignVal + bitsNeeded) % 8;

        return isNew;
    }

    private Token read(BitReader input, Ctx ctx, int nextIndex, String last)
    {
        Token retVal = null;

        // see encoder regarding byteAligned mode:
        // we require that the input stream knows its length, so that if it doesn't have enough bits available for
        // a full sequence, we can stop as soon as there aren't any bits to read a full sequence.
        // From our point of view, the remainder of the byte can just contain garbage.
        // A special case is when we had to write one or more sequences to fill up an entire byte
        // so we need to ignore the rest of the stream if it contains an escape char without room for an encoded char (at least 8bits)
        int bitsNeeded = this.bitsNeeded(nextIndex);
        if (input.hasNext(bitsNeeded)) {

            int code = input.read(bitsNeeded);

            // note that all extra bits we read below are either 8 or 16, so we only need to consider bitsNeeded
            ctx.byteAlignVal = (ctx.byteAlignVal + bitsNeeded) % 8;

            retVal = new Token();
            if (code == ASCII_ESCAPE_IDX || code == UNICODE_ESCAPE_IDX) {

                // this is the special end case in byteAligned mode
                if (this.config.byteAlignMode() && !input.hasNext(8)) {
                    // sync the input stream by reading the remaining bits until a full byte
                    if (ctx.byteAlignVal > 0) {
                        input.read(8 - ctx.byteAlignVal);
                    }
                    retVal = null;
                }
                else {

                    retVal.value = String.valueOf((char) input.read(code == ASCII_ESCAPE_IDX ? 8 : 16));

                    // see encoder (note: don't append the escape characters themselves)
                    // note that value should never be UNICODE_ESCAPE_VAL here, it's just there for completeness
                    retVal.isNew = !retVal.value.equals(ASCII_ESCAPE_VAL) && !retVal.value.equals(UNICODE_ESCAPE_VAL);
                }
            }
            else if (ctx.dict.hasKey(code)) {
                retVal.value = (String) ctx.dict.get(code);
            }
            // when the code is not in the dict, we have one valid edge case:
            // when we're reading a code that's equal to the next code we would add
            // It's the case where we're reading a code that hasn't been added to the dictionary yet,
            // but we can deduce what it must be:
            //
            // Let's say we're compressing "AAAA":
            // First we write 'A' (code 65)
            // Then we read 'A' again, and "AA" gets assigned eg. code 256
            // Then we write 256 for "AA"
            // Next, we encounter "AA" again - but something interesting happens...
            //
            // At this point in the decoder:
            // We read code 256
            // We need to output "AA"
            // Then we get next code and it's 257
            // BUT code 257 hasn't been added to our dictionary yet!
            //
            // However, we can figure out what code 257 must be. We know:
            //
            // It's a new dictionary entry being used right away
            // The last pattern was "AA"
            // Due to how LZW works, the new pattern must be the previous pattern + its first character
            else if (code == nextIndex) {
                // note last shouldn't be null here (first char), otherwise let it crash
                retVal.value = last + last.charAt(0);
            }
            else {
                throw new IllegalStateException("Invalid compressed data");
            }

            if (retVal != null) {
                if (ENABLE_DEBUG) Logger.log("\t READ \t\t" + retVal.value + "\t\t(code " + code + ", " + bitsNeeded + " bits, size " + ctx.dict.size() + ")");
            }
        }
        // ead of stream; let's eat the remaining bits if we're in byteAligned mode
        else {
            if (this.config.byteAlignMode() && ctx.byteAlignVal > 0) {
                // sync the input stream by reading the remaining bits until a full byte
                input.read(8 - ctx.byteAlignVal);
            }
        }

        return retVal;
    }

    private void checkReset(FlexDict dict)
    {
        if (this.config.enableDynamicDict() && this.config.enableDictReset() && dict.size() >= this.config.maxDictSize()) {
            if (ENABLE_DEBUG) Logger.log("RESET");
            dict.reset();
        }
    }

    private int bitsNeeded(int code)
    {
        // note: int is 4 bytes, so 32 bits
        return code == 0 ? 1 : 32 - Integer.numberOfLeadingZeros(code);
    }
}
