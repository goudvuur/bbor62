/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.ifaces;

/**
 * Created by bram on Nov 15, 2024
 */
public interface BitReader
{
    //-----CONSTANTS-----

    //-----VARIABLES-----

    //-----CONSTRUCTORS-----

    //-----PUBLIC METHODS-----
    /**
     * Reads numBits from the stream
     */
    int read(int numBits);

    /**
     * Tests if numBits are available for reading
     */
    boolean hasNext(int numBits);

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
}
