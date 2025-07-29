/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.ifaces;

/**
 * Created by bram on Nov 15, 2024
 */
public interface BitWriter
{
    //-----CONSTANTS-----

    //-----VARIABLES-----

    //-----CONSTRUCTORS-----

    //-----PUBLIC METHODS-----
    /**
     * Writes numBits bits (max 32) to the stream
     */
    void write(int value, int numBits);

    /**
     * Flushes trailing _write_ bits to the stream
     */
    void flush();

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
}
