/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.jackson;

import be.goudvuur.base.bbor62.Bbor;
import be.goudvuur.base.bbor62.ifaces.BitWriter;
import be.goudvuur.base.bbor62.ifaces.Compressor;
import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * Created by bram on Dec 05, 2024
 */
public class BborGenerator extends GeneratorBase
{
    //-----CONSTANTS-----

    //-----VARIABLES-----
    private final Bbor.Encoder encoder;
    private final BitWriter writer;
    private final Compressor compressor;
    private final ObjectMapper mapper;

    //-----CONSTRUCTORS-----
    // see JsonFactory._createUTF8Generator()
    public BborGenerator(ObjectMapper mapper, IOContext ctxt, int features, Bbor.Encoder encoder, Compressor compressor)
    {
        super(features, mapper, ctxt);

        this.encoder = encoder;
        this.writer = (BitWriter) ctxt.contentReference().getRawContent();
        this.compressor = compressor;

        this.mapper = mapper;
    }

    //-----PUBLIC METHODS-----
    @Override
    public void writeStartArray() throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeStartArray(int size) throws IOException
    {
        this.encoder.writeStartArray(this.writer, size);
    }
    @Override
    public void writeStartArray(Object forValue) throws IOException
    {
        if (forValue instanceof Object[] e) {
            this.writeStartObject(forValue, e.length);
        }
        else if (forValue instanceof Iterable<?> e) {
            this.writeStartObject(forValue, Iterables.size(e));
        }
        else {
            throw new IllegalStateException("Can't determine size of array; " + forValue);
        }
    }
    @Override
    public void writeStartArray(Object forValue, int size) throws IOException
    {
        this.encoder.writeStartArray(this.writer, size);
    }
    @Override
    public void writeEndArray() throws IOException
    {
        this.encoder.writeEndArray(this.writer);
    }
    @Override
    public void writeStartObject() throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeStartObject(Object forValue) throws IOException
    {
        if (forValue instanceof Map<?, ?> e) {
            this.writeStartObject(forValue, e.size());
        }
        else {
            // didn't think this would be so hard, but essentially, this uses our wrapper BeanPropertyWriter
            // to execute the same code during serializing, but instead return true/false instead.
            // The alternative was to implement cbor's variable-length objects encoding, but that wastes an extra byte
            // for every object and I wanted to avoid that
            try {
                final SerializerProvider prov = this.mapper.getSerializerProviderInstance();
                final JsonSerializer<Object> ser = prov.findValueSerializer(forValue.getClass());
                final int count = (int) Streams.stream(ser.properties())
                                               .filter(writer -> {
                                                   try {
                                                       return ((Custom.MyBeanPropertyWriter) writer).willSerializeAsField(forValue, prov);
                                                   }
                                                   catch (Exception e) {
                                                       throw new RuntimeException("Error while checking if property would get serialized; " + writer, e);
                                                   }
                                               })
                                               .count();

                this.writeStartObject(forValue, count);
            }
            catch (Exception e) {
                throw new IOException("Can't determine size of object; " + forValue, e);
            }
        }
    }
    @Override
    public void writeStartObject(Object forValue, int size) throws IOException
    {
        this.encoder.writeStartObject(this.writer, size);
    }
    @Override
    public void writeEndObject() throws IOException
    {
        this.encoder.writeEndObject(this.writer);
    }
    @Override
    public void writeFieldName(String name) throws IOException
    {
        this.encoder.writeFieldName(this.writer, this.compressor, name);
    }
    @Override
    public void writeString(String text) throws IOException
    {
        this.encoder.writeString(this.writer, this.compressor, text);
    }
    @Override
    public void writeString(char[] buffer, int offset, int len) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeRawUTF8String(byte[] buffer, int offset, int len) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeUTF8String(byte[] buffer, int offset, int len) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeRaw(String text) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeRaw(String text, int offset, int len) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeRaw(char c) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws IOException
    {
        //this.encoder.writeBytes()
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeNumber(int v) throws IOException
    {
        this.encoder.writeNumber(this.writer, v);
    }
    @Override
    public void writeNumber(long v) throws IOException
    {
        this.encoder.writeNumber(this.writer, v);
    }
    @Override
    public void writeNumber(BigInteger v) throws IOException
    {
        throw new IllegalStateException("not supported yet");
    }
    @Override
    public void writeNumber(double v) throws IOException
    {
        this.encoder.writeNumber(this.writer, v);
    }
    @Override
    public void writeNumber(float v) throws IOException
    {
        this.encoder.writeNumber(this.writer, v);
    }
    @Override
    public void writeNumber(BigDecimal v) throws IOException
    {
        throw new IllegalStateException("not supported yet");
    }
    @Override
    public void writeNumber(String encodedValue) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }
    @Override
    public void writeBoolean(boolean state) throws IOException
    {
        this.encoder.writeBoolean(this.writer, state);
    }
    @Override
    public void writeNull() throws IOException
    {
        this.encoder.writeNull(this.writer);
    }
    @Override
    public void flush() throws IOException
    {
        this.writer.flush();
    }
    @Override
    protected void _releaseBuffers()
    {
        // TODO release resources?
    }
    @Override
    protected void _verifyValueWrite(String typeMsg) throws IOException
    {
        throw new IllegalStateException("unimplemented");
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
}
