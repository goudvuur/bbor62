/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62;

import be.goudvuur.base.bbor62.ifaces.BitReader;

/**
 * This is just a wrapping reader around another reader with a limit number of bytes until hasNext() returns false.
 *
 * Created by bram on Nov 26, 2024
 */
public class WrappedByteReader implements BitReader
{
    //-----CONSTANTS-----

    //-----VARIABLES-----
    private final BitReader stream;
    private final long byteNum;
    private int bitsRead;
    private long bytesRead;

    //-----CONSTRUCTORS-----
    public WrappedByteReader(BitReader stream, long byteNum)
    {
        this.stream = stream;
        this.byteNum = byteNum;
        this.bitsRead = 0;
        this.bytesRead = 0;
    }

    //-----PUBLIC METHODS-----
    @Override
    public int read(int numBits)
    {
        int retVal = this.stream.read(numBits);

        this.bitsRead += numBits;
        this.bytesRead += Math.floorDiv(this.bitsRead, 8);
        this.bitsRead = this.bitsRead % 8;

        return retVal;
    }
    @Override
    public boolean hasNext(int numBits)
    {
        int newBitsRead = this.bitsRead + numBits;
        long newBytesRead = this.bytesRead + Math.floorDiv(newBitsRead, 8);
        newBitsRead = newBitsRead % 8;

        // we either haven't read all bytes yet, or we have read all bytes and align perfectly
        return newBytesRead < this.byteNum || (newBytesRead == this.byteNum && newBitsRead == 0);
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
}
