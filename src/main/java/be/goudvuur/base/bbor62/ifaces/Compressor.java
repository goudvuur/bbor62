/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.ifaces;

/**
 * Created by bram on Nov 25, 2024
 */
public interface Compressor
{
    //-----CONSTANTS-----

    //-----VARIABLES-----

    //-----PUBLIC METHODS-----
    void compress(String input, BitWriter output);
    String decompress(BitReader input);
}
