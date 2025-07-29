/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.jackson;

import be.goudvuur.base.bbor62.Bbor;
import be.goudvuur.base.bbor62.ifaces.BitReader;
import be.goudvuur.base.bbor62.ifaces.Compressor;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.DupDetector;
import com.fasterxml.jackson.core.json.JsonReadContext;
import com.fasterxml.jackson.core.json.PackageVersion;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.Map;

/**
 * This started out as a skeleton implementation of ParserMinimalBase
 * Then, copied some @Override methods from ParserBase
 * then, copied some @Override methods from UTF8StreamJsonParser
 * then, followed the code trail from JsonFactory.createParser(String)
 * <p>
 * Created by bram on Dec 04, 2024
 */
public class BborParser extends ParserMinimalBase
{
    //-----CONSTANTS-----
    private static final Map<Bbor.Decoder.Token, JsonToken> TOKEN_MAPPING =
                    Map.ofEntries(
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.END_OBJECT, JsonToken.END_OBJECT),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.START_OBJECT, JsonToken.START_OBJECT),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.END_ARRAY, JsonToken.END_ARRAY),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.START_ARRAY, JsonToken.START_ARRAY),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.FIELD_NAME, JsonToken.FIELD_NAME),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.VALUE_STRING, JsonToken.VALUE_STRING),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_INT),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.VALUE_NUMBER_FLOAT, JsonToken.VALUE_NUMBER_FLOAT),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.VALUE_TRUE, JsonToken.VALUE_TRUE),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.VALUE_FALSE, JsonToken.VALUE_FALSE),
                                    new AbstractMap.SimpleImmutableEntry<>(Bbor.Decoder.Token.VALUE_NULL, JsonToken.VALUE_NULL)
                    );

    //-----VARIABLES-----
    // from ParserBase
    private final JsonReadContext _parsingContext;
    // from ParserBase
    protected final IOContext _ioContext;
    // from ParserBase
    protected final StreamReadConstraints _streamReadConstraints;
    // from ParserBase
    private boolean _closed;
    // from UTF8StreamJsonParser
    private ObjectCodec _objectCodec;
    // from ParserBase
    protected long _tokenInputTotal;
    // from ParserBase
    protected int _tokenInputRow = 1;
    // from ParserBase
    protected int _tokenInputCol;

    private final Bbor.Decoder decoder;
    private final BitReader reader;
    private final Compressor compressor;

    //-----CONSTRUCTORS-----
    public BborParser(ObjectCodec codec, IOContext ctxt, int features, Bbor.Decoder decoder, Compressor compressor)
    {
        super(features);

        // from ParserBase
        this._ioContext = ctxt;

        // from ParserBase
        final StreamReadConstraints streamReadConstraints = ctxt.streamReadConstraints();
        this._streamReadConstraints = streamReadConstraints == null ?
                                      StreamReadConstraints.defaults() : streamReadConstraints;

        // from ParserBase
        DupDetector dups = Feature.STRICT_DUPLICATE_DETECTION.enabledIn(features)
                           ? DupDetector.rootDetector(this) : null;
        this._parsingContext = JsonReadContext.createRootContext(dups);

        // if null, will be set automatically by the codec using setCodec()
        this._objectCodec = codec;
        this._closed = false;

        this.decoder = decoder;
        this.reader = (BitReader) ctxt.contentReference().getRawContent();
        this.compressor = compressor;
    }

    //-----PUBLIC METHODS-----
    @Override
    public JsonToken nextToken() throws IOException
    {
        Bbor.Decoder.Token token = this.decoder.readNext(this.reader, this.compressor);
        if (token != null) {
            this._currToken = TOKEN_MAPPING.get(token);
            if (this._currToken == null) {
                throw new IllegalStateException("Encounted a bbor token that has no json token counterpart, please fix this; " + token);
            }

            if (this.decoder.ctx != null && this.decoder.ctx.field instanceof String) {
                this._parsingContext.setCurrentName((String) this.decoder.ctx.field);
            }
            else {
                this._parsingContext.setCurrentName(null);
            }
        }
        else {
            this._currToken = null;
        }

        return this._currToken;
    }
    // from ParserBase
    @Override
    protected void _handleEOF() throws JsonParseException
    {
        if (!_parsingContext.inRoot()) {
            String marker = _parsingContext.inArray() ? "Array" : "Object";
            _reportInvalidEOF(String.format(": expected close marker for %s (start marker at %s)",
                                            marker,
                                            _parsingContext.startLocation(_contentReference())),
                              null);
        }
    }
    // from ParserBase
    @Override
    public String getCurrentName() throws IOException
    {
        if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
            JsonReadContext parent = _parsingContext.getParent();
            if (parent != null) {
                return parent.getCurrentName();
            }
        }
        return _parsingContext.getCurrentName();
    }
    // from UTF8StreamJsonParser
    @Override
    public ObjectCodec getCodec()
    {
        return this._objectCodec;
    }
    // from UTF8StreamJsonParser
    @Override
    public void setCodec(ObjectCodec oc)
    {
        this._objectCodec = oc;
    }
    // from ParserBase
    @Override
    public Version version()
    {
        return PackageVersion.VERSION;
    }
    // edited from ParserBase
    @Override
    public void close() throws IOException
    {
        if (!_closed) {
            _closed = true;
            try {
                // TODO release resources?
                //_closeInput();
            }
            finally {
                _ioContext.close();
            }
        }
    }
    // from ParserBase
    @Override
    public boolean isClosed()
    {
        return this._closed;
    }
    // from ParserBase
    @Override
    public JsonStreamContext getParsingContext()
    {
        return this._parsingContext;
    }
    // from ParserBase
    @Override
    public JsonLocation getTokenLocation()
    {
        return new JsonLocation(_contentReference(),
                                -1L, getTokenCharacterOffset(), // bytes, chars
                                getTokenLineNr(),
                                getTokenColumnNr());
    }
    @Override
    public JsonLocation getCurrentLocation()
    {
        return JsonLocation.NA;
    }
    @Override
    public void overrideCurrentName(String name)
    {
        throw new IllegalStateException("Unimplemented");
    }
    // edited from UTF8StreamJsonParser
    @Override
    public String getText() throws IOException
    {
        if (_currToken == null) {
            return null;
        }
        switch (_currToken) {
            case FIELD_NAME:
                return _parsingContext.getCurrentName();
            case VALUE_STRING:
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return String.valueOf(this.decoder.value);
            default:
                return _currToken.asString();
        }
    }
    @Override
    public char[] getTextCharacters() throws IOException
    {
        return this.getText().toCharArray();
    }
    // edited from ParserBase
    @Override
    public boolean hasTextCharacters()
    {
        if (_currToken == JsonToken.VALUE_STRING) {
            return true;
        } // usually true
        if (_currToken == JsonToken.FIELD_NAME) {
            return true;
        } // should be in this.decoder.ctx.field, no?
        return false;
    }
    // edited from ParserBase
    @Override
    public Number getNumberValue() throws IOException
    {
        switch (this.getNumberType()) {
            case INT:
                return ((Long) this.decoder.value).intValue();
            case LONG:
                return (long) this.decoder.value;
            case FLOAT:
                return (float) this.decoder.value;
            case DOUBLE:
                return (double) this.decoder.value;
        }

        this._throwInternal();
        return null;
    }
    @Override
    public NumberType getNumberType() throws IOException
    {
        switch (this.decoder.primitive) {
            case BYTE:
                return NumberType.INT;
            case POSITIVE_INTEGER:
            case NEGATIVE_INTEGER:
                // watch out: the max/min of a java int doesn't align with the max/min of cbor int
                long intVal = (long) this.decoder.value;
                return intVal >= Integer.MIN_VALUE && intVal <= Integer.MAX_VALUE ? NumberType.INT : NumberType.LONG;
            case POSITIVE_BIGNUM:
            case NEGATIVE_BIGNUM:
                return NumberType.LONG;
            case FLOAT16:
            case FLOAT32:
                return NumberType.FLOAT;
            case FLOAT64:
                return NumberType.DOUBLE;
        }

        this._throwInternal();
        return null;
    }
    @Override
    public int getIntValue() throws IOException
    {
        return ((Long) this.decoder.value).intValue();
    }
    @Override
    public long getLongValue() throws IOException
    {
        return (long) this.decoder.value;
    }
    @Override
    public BigInteger getBigIntegerValue() throws IOException
    {
        throw new IllegalStateException("Unimplemented");
    }
    @Override
    public float getFloatValue() throws IOException
    {
        return (float) this.decoder.value;
    }
    @Override
    public double getDoubleValue() throws IOException
    {
        return (double) this.decoder.value;
    }
    @Override
    public BigDecimal getDecimalValue() throws IOException
    {
        throw new IllegalStateException("Unimplemented");
    }
    // edited from UTF8StreamJsonParser
    @Override
    public int getTextLength() throws IOException
    {
        if (_currToken != null) { // null only before/after document
            switch (_currToken) {
                case FIELD_NAME:
                    return _parsingContext.getCurrentName().length();
                case VALUE_STRING:
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                    return String.valueOf(this.decoder.value).length();
                default:
                    return _currToken.asCharArray().length;
            }
        }
        return 0;
    }
    @Override
    public int getTextOffset() throws IOException
    {
        return 0;
    }
    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException
    {
        throw new IllegalStateException("Unimplemented");
    }

    //-----PROTECTED METHODS-----
    // from ParserBase
    protected ContentReference _contentReference()
    {
        if (JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION.enabledIn(_features)) {
            return _ioContext.contentReference();
        }
        return _contentReferenceRedacted();
    }
    // from ParserBase
    protected ContentReference _contentReferenceRedacted()
    {
        return ContentReference.redacted();
    }
    // from ParserBase
    protected long getTokenCharacterOffset()
    {
        return _tokenInputTotal;
    }
    // from ParserBase
    protected int getTokenLineNr()
    {
        return _tokenInputRow;
    }
    // from ParserBase
    protected int getTokenColumnNr()
    {
        // note: value of -1 means "not available"; otherwise convert from 0-based to 1-based
        int col = _tokenInputCol;
        return (col < 0) ? col : (col + 1);
    }

    //-----PRIVATE METHODS-----
}
