/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62;

import be.goudvuur.base.bbor62.ifaces.BitReader;
import be.goudvuur.base.bbor62.ifaces.BitWriter;

/**
 * Created by bram on Nov 18, 2024
 */
public class ByteStream implements BitReader, BitWriter
{
    //-----CONSTANTS-----

    //-----VARIABLES-----
    private byte[] buffer;
    private int readPos = 0;
    private int readBitPos = 0;
    private int writePos = 0;
    private int writeBitPos = 0;
    private int writeBuf;

    //-----CONSTRUCTORS-----
    public ByteStream()
    {
        this.buffer = new byte[8];
        this.writePos = 0;
    }

    //-----PUBLIC METHODS-----
    @Override
    public int read(int numBits)
    {
        // we're returning an int
        if (numBits <= 0 || numBits > 32) {
            throw new IllegalArgumentException("Number of bits must be between 1 and 32");
        }

        int result = 0;
        int bitsRemaining = numBits;

        while (bitsRemaining > 0) {
            int currentValue = this.buffer[this.readPos];
            int bitsAvailable = 8 - this.readBitPos;
            int bitsToRead = Math.min(bitsRemaining, bitsAvailable);
            int mask = (1 << bitsToRead) - 1;
            int shift = bitsAvailable - bitsToRead;
            int extractedBits = (currentValue >>> shift) & mask;

            result = (result << bitsToRead) | extractedBits;
            bitsRemaining -= bitsToRead;
            this.readBitPos += bitsToRead;

            if (this.readBitPos >= 8) {
                this.readPos++;
                this.readBitPos -= 8;
            }
        }

        return result;
    }
    @Override
    public boolean hasNext(int numBits)
    {
        return (this.readPos * 8 + this.readBitPos + numBits) <= this.writePos * 8;
    }
    @Override
    public void write(int value, int numBits)
    {
        if (numBits <= 0 || numBits > 32) {
            throw new IllegalArgumentException("Number of bits must be between 1 and 32");
        }
        if (value >= (1 << numBits)) {
            throw new IllegalArgumentException("Value is too large for the specified number of bits");
        }

        while (numBits > 0) {

            int bitsAvailable = 8 - this.writeBitPos;
            int bitsToWrite = Math.min(numBits, bitsAvailable);
            int remainingBits = numBits - bitsToWrite;

            int mask = (1 << bitsToWrite) - 1;
            int val = (value >>> remainingBits) & mask;

            this.writeBuf = (this.writeBuf << bitsToWrite) | val;
            this.writeBitPos += bitsToWrite;

            if (this.writeBitPos == 8) {
                this.assertSpaceFor(1);
                this.buffer[this.writePos++] = (byte) this.writeBuf;
                this.writeBuf = 0;
                this.writeBitPos = 0;
            }

            numBits = remainingBits;
        }
    }
    @Override
    public void flush()
    {
        // Handle partial chunk if any
        if (this.writeBitPos > 0) {
            throw new IllegalStateException("Remaining bits left in the byte stream, this shouldn't happen; " + this.writeBitPos);
        }
    }
    public long length()
    {
        // the writePos is the position where the next byte will be written,
        // so it's the length of all complete bytes
        return this.writePos;
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
    private void assertSpaceFor(int numBytes)
    {
        // the readPos is the position where the current (uncomplete) byte can be read
        // as soon as a complete byte was read, the readPos is incremented to the next (empty) byte,
        // so all positions before readPos can be assumed read

        // the writePos is the position where the next byte will be written (the one we're currently building in writeBuf)

        // compact if some bytes were already read back, so we don't grow indefinitely and hopefully don't have to grow at all
        // this will copy the bytes starting from the current read position to the beginning of the buffer
        if (this.readPos > 0) {
            // let's keep the same length
            byte[] newBuffer = new byte[this.buffer.length];
            System.arraycopy(this.buffer, this.readPos, newBuffer, 0, this.buffer.length - this.readPos);
            this.buffer = newBuffer;
            // writePos should >= readPos, so this should always work
            this.writePos -= this.readPos;
            this.readPos = 0;
        }

        // copy to a new, larger buffer if we don't have any room left, despite compacting
        if (this.writePos + numBytes > this.buffer.length) {
            // let's use a grow ratio of x2, same as ArrayList
            byte[] newBuffer = new byte[this.buffer.length * 2];
            System.arraycopy(this.buffer, 0, newBuffer, 0, this.buffer.length);
            this.buffer = newBuffer;
        }
    }
}
