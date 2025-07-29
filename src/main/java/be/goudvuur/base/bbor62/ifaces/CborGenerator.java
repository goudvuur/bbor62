/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.ifaces;

/**
 * This class is inspired by com.fasterxml.jackson.core.JsonGenerator
 *
 * Created by bram on Nov 26, 2024
 */
public interface CborGenerator
{
    //-----CONSTANTS-----

    //-----VARIABLES-----

    //-----PUBLIC METHODS-----
    CborGenerator write(BitWriter outputStream, Compressor compressor, Object value);

    CborGenerator writeNull(BitWriter outputStream);

    CborGenerator writeBoolean(BitWriter outputStream, boolean value);

    CborGenerator writeNumber(BitWriter outputStream, Number value);

    CborGenerator writeString(BitWriter outputStream, Compressor compressor, String value);

    CborGenerator writeBytes(BitWriter outputStream, byte[] value);

    CborGenerator writeFieldName(BitWriter outputStream, Compressor compressor, String value);

    CborGenerator writeStartArray(BitWriter outputStream, long size);

    CborGenerator writeEndArray(BitWriter outputStream);

    CborGenerator writeStartObject(BitWriter outputStream, long size);

    CborGenerator writeEndObject(BitWriter outputStream);

}
