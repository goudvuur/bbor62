/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62;

import be.goudvuur.base.bbor62.ifaces.BitReader;
import be.goudvuur.base.bbor62.ifaces.BitWriter;
import be.goudvuur.base.bbor62.ifaces.Compressor;
import be.goudvuur.base.bbor62.jackson.BborGenerator;
import be.goudvuur.base.bbor62.jackson.BborParser;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * This is a serializer that combines Bbor + LZW string compression + Base62 to string conversion
 *
 * Created by bram on Nov 27, 2024
 */
public class Bbor62<T>
{
    public interface Config
    {

        LZW.Config lzwConfig();
        BaseXStream.Config baseXConfig();
        Bbor.Config bborConfig();
    }

    //-----CONSTANTS-----
    public static final Config DEFAULT_CONFIG = new Config()
    {
        @Override
        public LZW.Config lzwConfig()
        {
            return LZW.DEFAULT_CONFIG;
        }
        @Override
        public BaseXStream.Config baseXConfig()
        {
            return BaseXStream.DEFAULT_CONFIG;
        }
        @Override
        public Bbor.Config bborConfig()
        {
            return Bbor.DEFAULT_CONFIG;
        }
    };

    //-----VARIABLES-----

    //-----CONSTRUCTORS-----

    //-----PUBLIC METHODS-----
    public static <T> String encode(T value) throws IOException
    {
        return encode(value, DEFAULT_CONFIG);
    }

    public static <T> String generate(T value) throws IOException
    {
        return generate(value, DEFAULT_CONFIG);
    }

    public static <T> T decode(String cbor62) throws IOException
    {
        return decode(cbor62, DEFAULT_CONFIG);
    }

    public static <T> T parse(String cbor62, Class<T> clazz) throws IOException
    {
        return parse(cbor62, clazz, DEFAULT_CONFIG);
    }

    /**
     * Encodes the value by passing it directly to the bbor encoder (note that it supports more than json maps),
     * without using any Jackson wrappers
     */
    public static <T> String encode(T value, Config config) throws IOException
    {
        // debatable if we should automatically convert a json string to a Map
        // disabled because I think this is te responsibility of the caller
        //value = jsonToObj(value);

        StringBuilder base62 = new StringBuilder();

        BitWriter writer = new BaseXStream.Encoder(base62::append, config.baseXConfig());

        // needs to be byte aligned so we can write out compressed strings as byte arrays
        // Also note that this will be reused over and over again for each string in the json file,
        // reusing and adding to the dict, so make sure the sync with the decoder
        Compressor compressor = new LZW(config.lzwConfig());

        new Bbor.Encoder(config.bborConfig()).write(writer, compressor, value);

        // make sure to write the last base62 block if it's partial
        writer.flush();

        return base62.toString();
    }

    /**
     * Basically does the same as encode(), but uses the Jackson generator instead, so all Jackson-annotated
     * fields, getters, etc are taken into account
     */
    public static <T> String generate(T value, Config config) throws IOException
    {
        StringBuilder base62 = new StringBuilder();

        BitWriter writer = new BaseXStream.Encoder(base62::append, config.baseXConfig());

        // needs to be byte aligned so we can write out compressed strings as byte arrays
        // Also note that this will be reused over and over again for each string in the json file,
        // reusing and adding to the dict, so make sure the sync with the decoder
        Compressor compressor = new LZW(config.lzwConfig());

        ObjectMapper objectMapper = getObjectMapper();

        IOContext writeContext = new IOContext(StreamReadConstraints.defaults(),
                                               StreamWriteConstraints.defaults(),
                                               ErrorReportConfiguration.defaults(),
                                               objectMapper.getFactory()._getBufferRecycler(),
                                               ContentReference.rawReference(writer),
                                               false);

        JsonGenerator generator = new BborGenerator(objectMapper,
                                                        writeContext,
                                                        objectMapper.getFactory().getFactoryFeatures(),
                                                        new Bbor.Encoder(config.bborConfig()),
                                                        compressor
        );

        // note that the objectMapper calls generator.flush()
        objectMapper.writeValue(generator, value);

        return base62.toString();
    }

    /**
     * Inverse of encode() in that it decodes the raw object that was encoded in the first place
     */
    public static <T> T decode(String cbor62, Config config) throws IOException
    {
        // sync with encode()

        BitReader reader = new BaseXStream.Decoder(cbor62, config.baseXConfig());

        Compressor compressor = new LZW(config.lzwConfig());

        // note that cbor always decodes to the raw (encoded) object (map, string, number, array, ...)
        return (T) new Bbor.Decoder(config.bborConfig()).read(reader, compressor);
    }

    /**
     * Inverse of generate() in that it uses the Jackson parser to recreate the requested POJO class
     */
    public static <T> T parse(String cbor62, Class<T> clazz, Config config) throws IOException
    {
        // let's test for good and create a new instance
        Compressor compressor = new LZW(config.lzwConfig());

        BitReader reader = new BaseXStream.Decoder(cbor62, config.baseXConfig());

        ObjectMapper objectMapper = getObjectMapper();

        IOContext readContext = new IOContext(StreamReadConstraints.defaults(),
                                              StreamWriteConstraints.defaults(),
                                              ErrorReportConfiguration.defaults(),
                                              objectMapper.getFactory()._getBufferRecycler(),
                                              ContentReference.rawReference(reader),
                                              false);

        JsonParser parser = new BborParser(objectMapper,
                                           readContext,
                                           objectMapper.getFactory().getFactoryFeatures(),
                                           new Bbor.Decoder(config.bborConfig()),
                                           compressor
        );

        return objectMapper.readerFor(clazz).readValue(parser);
    }

    /**
     * Auto-converts a json string to a Map if json is detected
     */
    public static <T> T jsonToObj(T value) throws IOException
    {
        if (value instanceof String valueStr && (valueStr.trim().startsWith("{") || valueStr.trim().startsWith("["))) {
            // this will convert a json string to a regular Map
            value = getObjectMapper().readValue(valueStr, new TypeReference<>()
            {
            });
        }
        return value;
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
    private static ObjectMapper getObjectMapper()
    {
        return new ObjectMapper();
    }
}
