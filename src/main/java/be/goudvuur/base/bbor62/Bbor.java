/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62;

import be.goudvuur.base.bbor62.ifaces.BitReader;
import be.goudvuur.base.bbor62.ifaces.BitWriter;
import be.goudvuur.base.bbor62.ifaces.CborGenerator;
import be.goudvuur.base.bbor62.ifaces.Compressor;

import java.util.*;

/**
 * This started out as a conversion of the original cbor.js (see https://github.com/paroga/cbor-js)
 * I added:
 * - string compression
 * - implemented better number encoding
 * - added field caching
 * <p>
 * All changed are non-standardized breaking changes (except for the improved number handling)
 * because:
 * - I wanted to pack the bytes as densely as possible
 * - I wanted to make the implementation as simple as possible for the JS port
 * <p>
 * Notably, I didn't use:
 * - the stringref tags to compress the field names (http://cbor.schmorp.de/stringref)
 * - custom (or existing?) compressed string algorithm tags for string value compression
 * <p>
 * <p>
 * Also see https://www.rfc-editor.org/rfc/rfc8949.html
 * and https://www.iana.org/assignments/cbor-tags/cbor-tags.xhtml
 * <p>
 * Created by bram on Oct 31, 2024
 */
public class Bbor
{
    public interface Config
    {
        /**
         * toggle the creation of a dynamic dictionary that maps unknown fields to numbers instead of encoding them
         */
        boolean enableKeyMapping();

        /**
         * next to passing a null compressor, this is a general kill switch we can use to disable string compression
         */
        boolean enableStringCompression();

        /**
         * returns a number of very common json fields we might as well seed the kep mapping with
         * indexed by field name
         */
        Map<Object, Object> staticFields();

        /**
         * the reverse of staticFields(), indexed by field index
         */
        Map<Object, Object> staticFieldsRev();
    }

    public static final Config DEFAULT_CONFIG = new Config()
    {
        // 10 very common field names to seed the field dict with
        private static final String[] INITIAL_FIELDS = {
                        "id",
                        "type",
                        "name",
                        "status",
                        "count",
                        "data",
                        "value",
                        "error",
                        "response",
                        "version"
        };

        // Static fields for encoding and decoding (doesn't need to be in predictable order)
        // String -> Integer
        private static final Map<Object, Object> STATIC_FIELDS = new HashMap<>();
        // Integer -> String
        private static final Map<Object, Object> STATIC_FIELDS_REV = new HashMap<>();  // 0-63 plus escapes

        static {
            for (int i = 0; i < INITIAL_FIELDS.length; i++) {
                String c = INITIAL_FIELDS[i];
                if (!STATIC_FIELDS.containsKey(c)) {
                    STATIC_FIELDS.put(c, i);
                    STATIC_FIELDS_REV.put(i, c);
                }
                else {
                    throw new IllegalArgumentException(String.format("Duplicate dict character detected '%s'", c));
                }
            }
        }

        @Override
        public boolean enableKeyMapping()
        {
            return true;
        }
        @Override
        public boolean enableStringCompression()
        {
            return true;
        }
        @Override
        public Map<Object, Object> staticFields()
        {
            return STATIC_FIELDS;
        }
        @Override
        public Map<Object, Object> staticFieldsRev()
        {
            return STATIC_FIELDS_REV;
        }
    };

    private static final double POW_2_24 = Math.pow(2, 24);
    private static final long POW_2_16 = (long) Math.pow(2, 16);
    private static final long POW_2_32 = (long) Math.pow(2, 32);
    // Number.MAX_SAFE_INTEGER in JavaScript
    private static final long JS_MAX_SAFE_INTEGER = (long) (Math.pow(2, 53) - 1);

    public static class Encoder implements CborGenerator
    {
        private final Config config;
        // structure to compress the fields to long values
        private final FlexDict fields;

        public Encoder(Config config)
        {
            this.config = config;
            this.fields = new FlexDict(this.config.staticFields());
        }

        @Override
        public CborGenerator write(BitWriter outputStream, Compressor compressor, Object value)
        {
            if (value == null) {
                return this.writeNull(outputStream);
            }

            if (value instanceof Boolean) {
                return this.writeBoolean(outputStream, (Boolean) value);
            }

            // note that we can't support javascript undefined because it doesn't exist in Java

            if (value instanceof Number) {
                return this.writeNumber(outputStream, (Number) value);
            }

            if (value instanceof String) {
                return this.writeString(outputStream, compressor, (String) value);
            }

            if (value instanceof byte[]) {
                return this.writeBytes(outputStream, (byte[]) value);
            }

            if (value instanceof List) {
                List<?> array = (List<?>) value;
                this.writeStartArray(outputStream, array.size());
                for (Object item : array) {
                    write(outputStream, compressor, item);
                }
                this.writeEndArray(outputStream);
                return this;
            }

            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                this.writeStartObject(outputStream, map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    this.writeFieldName(outputStream, compressor, (String) entry.getKey());
                    this.write(outputStream, compressor, entry.getValue());
                }
                this.writeEndObject(outputStream);
                return this;
            }

            throw new IllegalStateException("Unsupported type: " + value.getClass());
        }
        @Override
        public CborGenerator writeNull(BitWriter outputStream)
        {
            writeUint8(outputStream, 0xf6);
            return this;
        }
        @Override
        public CborGenerator writeBoolean(BitWriter outputStream, boolean value)
        {
            writeUint8(outputStream, value ? 0xf5 : 0xf4);
            return this;
        }
        @Override
        public CborGenerator writeNumber(BitWriter outputStream, Number value)
        {
            double doubleValue = value.doubleValue();

            // future improvement: also support BigInteger
            if (Math.floor(doubleValue) == doubleValue && Math.abs(doubleValue) <= Long.MAX_VALUE) {

                long longValue = value.longValue();

                if (longValue >= 0) {
                    if (longValue <= JS_MAX_SAFE_INTEGER) {
                        // 0: Unsigned integer
                        writeTypeAndLength(outputStream, 0, longValue);
                        return this;
                    }
                    // long larger than JS_MAX_SAFE_INTEGER
                    else {
                        // major type 6 (tag), Tag 2 (positive bignum) = 0xc2
                        writeUint8(outputStream, 0xc2);
                        byte[] bytes = this.toPositiveBigNum(longValue);
                        // write out the long as a normal byte string
                        writeTypeAndLength(outputStream, 2, bytes.length);
                        for (byte b : bytes) {
                            this.writeUint8(outputStream, b);
                        }
                        return this;
                    }
                }
                else {
                    if (-JS_MAX_SAFE_INTEGER <= longValue) {
                        // 1: Negative integer
                        // Note that negative int uses its additional information byte in a similar way to type unsigned int,
                        // but the values are interpreted as -1 minus the encoded unsigned number:
                        writeTypeAndLength(outputStream, 1, -1L - longValue);
                        return this;
                    }
                    // long larger than JS_MAX_SAFE_INTEGER
                    else {
                        // major type 6 (tag), Tag 3 (negative bignum) = 0xc3
                        writeUint8(outputStream, 0xc3);
                        // For negative bignums, we need to encode -1-n
                        // Example: -257 becomes tag(3) + bytes([0x01, 0x00])
                        byte[] bytes = this.toPositiveBigNum(-1L - longValue);
                        // write out the long as a normal byte string
                        writeTypeAndLength(outputStream, 2, bytes.length);
                        for (byte b : bytes) {
                            this.writeUint8(outputStream, b);
                        }
                        return this;
                    }
                }
            }

            // Decimals are encoded with major type 7, with three possible formats:
            // Half precision (16-bit IEEE 754) - additional info 25 (0xf9) <-- ignored, doesn't exist in Java (there's a ref implementation in the decoder)
            // Single precision (32-bit IEEE 754) - additional info 26 (0xfa)
            // Double precision (64-bit IEEE 754) - additional info 27 (0xfb)
            // future improvement: also support BigDecimal
            float floatValue = value.floatValue();
            // For normal values (we're ignoring NaN, Inf, +0, -0), check if the relative difference is within float's precision
            // Float has ~6-7 decimal digits of precision, so we use 1e-7 as threshold
            // Note that the significance of a difference depends on the scale of the numbers involved, hence the division
            if (Math.abs((doubleValue - floatValue) / doubleValue) < 1e-7) {
                writeUint8(outputStream, 0xfa);
                writeFloat32(outputStream, floatValue);
            }
            else {
                writeUint8(outputStream, 0xfb);
                writeFloat64(outputStream, doubleValue);
            }

            return this;
        }
        @Override
        public CborGenerator writeString(BitWriter outputStream, Compressor compressor, String value)
        {
            if (this.config.enableStringCompression() && compressor != null && !value.isEmpty()) {

                // to be able to encode the length first, we can't write to this.encoder directly
                // we must encode to a temp bytestream (cbor counts in bytes, so let's byte-align the LZW encoder and write to a ByteStream)
                ByteStream byteStream = new ByteStream();
                //lzw.encode(str, this.encoder);
                // note that the LZW encoder is configured to be byte aligned (and will call byteStream.flush() to check it)
                compressor.compress(value, byteStream);
                // this will check if the stream is byte aligned
                byteStream.flush();
                // let's write a string out as a string type, not byte[], so we know we need to return a string, not byte[]
                writeTypeAndLength(outputStream, 3, byteStream.length());
                // read the stream back and copy to the encoder stream
                while (byteStream.hasNext(8)) {
                    this.writeUint8(outputStream, byteStream.read(8));
                }
                return this;
            }
            else {
                byte[] utf8Data = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                writeTypeAndLength(outputStream, 3, utf8Data.length);
                for (byte b : utf8Data) {
                    this.writeUint8(outputStream, b);
                }
                return this;
            }
        }
        @Override
        public CborGenerator writeBytes(BitWriter outputStream, byte[] value)
        {
            writeTypeAndLength(outputStream, 2, value.length);
            for (byte b : value) {
                this.writeUint8(outputStream, b);
            }
            return this;
        }
        @Override
        public CborGenerator writeFieldName(BitWriter outputStream, Compressor compressor, String value)
        {
            if (this.config.enableKeyMapping()) {
                if (!this.fields.hasKey(value)) {
                    this.writeString(outputStream, compressor, value);
                    this.fields.add(value, this.fields.size());
                }
                else {
                    // since we encode fields using the map size, this needs to be an int
                    this.writeNumber(outputStream, (int) this.fields.get(value));
                }
            }
            else {
                this.writeString(outputStream, compressor, value);
            }

            return this;
        }
        @Override
        public CborGenerator writeStartArray(BitWriter outputStream, long size)
        {
            writeTypeAndLength(outputStream, 4, size);
            return this;
        }
        @Override
        public CborGenerator writeEndArray(BitWriter outputStream)
        {
            // NOOP
            return this;
        }
        @Override
        public CborGenerator writeStartObject(BitWriter outputStream, long size)
        {
            writeTypeAndLength(outputStream, 5, size);
            return this;
        }
        @Override
        public CborGenerator writeEndObject(BitWriter outputStream)
        {
            // NOOP
            return this;
        }

        private void writeTypeAndLength(BitWriter outputStream, int type, long length)
        {
            if (length < 24) {
                // 0-23: Value is directly encoded in this byte (0-23)
                writeUint8(outputStream, (type << 5) | (int) length);
            }
            else if (length < 0x100L) {
                // 24: Value is in next byte (uint8_t)
                writeUint8(outputStream, (type << 5) | 24);
                writeUint8(outputStream, (int) length);
            }
            else if (length < 0x10000L) {
                // 25: Value is in next 2 bytes (uint16_t)
                writeUint8(outputStream, (type << 5) | 25);
                writeUint16(outputStream, (int) length);
            }
            else if (length < 0x100000000L) {
                // 26: Value is in next 4 bytes (uint32_t)
                writeUint8(outputStream, (type << 5) | 26);
                // watch out: if the value uses 32 bits instead of 31, eg. 4294967295 will be cast/converted to -1
                // but the bits will remain the same, so it's safe to do it this way. Watch out in the decoder though.
                writeUint32(outputStream, (int) length);
            }
            else {
                // 27: Value is in next 8 bytes (uint64_t)
                writeUint8(outputStream, (type << 5) | 27);
                writeUint64(outputStream, length);
            }
        }

        private void writeUint8(BitWriter outputStream, int value)
        {
            outputStream.write(value, 8);
        }

        private void writeUint16(BitWriter outputStream, int value)
        {
            outputStream.write(value, 16);
        }

        private void writeUint32(BitWriter outputStream, int value)
        {
            outputStream.write(value, 32);
        }

        private void writeUint64(BitWriter outputStream, long value)
        {
            int low = (int) (value % POW_2_32);
            int high = (int) ((value - low) / POW_2_32);
            outputStream.write(high, 32);
            outputStream.write(low, 32);
        }

        private void writeFloat32(BitWriter outputStream, float value)
        {
            // follows the IEEE 754 floating-point "single format" bit layout
            this.writeUint32(outputStream, Float.floatToIntBits(value));
        }

        private void writeFloat64(BitWriter outputStream, double value)
        {
            // follows the IEEE 754 floating-point "double format" bit layout
            this.writeUint64(outputStream, Double.doubleToLongBits(value));
        }

        private byte[] toPositiveBigNum(long value)
        {
            // Count leading zero bytes from MSB (reverses bytes)
            byte[] bytes = new byte[8];
            for (int i = 0; i < 8; i++) {
                bytes[7 - i] = (byte) (value & 0xFF);
                value >>>= 8;
            }

            // Find first non-zero byte (starting with MSB because we reversed)
            // Note the < 7 so we always have one byte so zero is correctly encoded
            int startIndex = 0;
            while (startIndex < 7 && bytes[startIndex] == 0) {
                startIndex++;
            }

            // Copy significant bytes (including trailing zeros)
            byte[] result = new byte[8 - startIndex];
            System.arraycopy(bytes, startIndex, result, 0, result.length);
            return result;
        }
    }

    public static class Decoder
    {
        /**
         * These tokens are mapped directly to Jackson's JsonToken
         * (except for raw)
         */
        public enum Token
        {
            END_OBJECT,
            START_OBJECT(END_OBJECT),
            END_ARRAY,
            START_ARRAY(END_ARRAY),
            FIELD_NAME,
            VALUE_STRING,
            VALUE_NUMBER_INT,
            VALUE_NUMBER_FLOAT,
            VALUE_TRUE,
            VALUE_FALSE,
            VALUE_NULL;

            public final Token rev;
            Token()
            {
                this(null);
            }
            Token(Token rev)
            {
                this.rev = rev;
            }
        }

        /**
         * These are the cbor primitives we currently support.
         * Note that these are NOT equal to Java/Javascript equivalent primitives
         * (eg. in cbor, an unsigned int can be max [0 to 2^64-1] where a Java int is in [-2^31-1 to 2^31-1])
         */
        public enum Primitive
        {
            POSITIVE_INTEGER,
            NEGATIVE_INTEGER,
            BYTE_STRING,
            TEXT_STRING,
            POSITIVE_BIGNUM,
            NEGATIVE_BIGNUM,
            BOOLEAN,
            NULL,
            UNDEFINED,
            BYTE,
            FLOAT16,
            FLOAT32,
            FLOAT64,
        }

        public static class Ctx
        {
            // the previous ctx
            Ctx prev;
            // the token type that started this ctx
            Token token;
            // the expected number of tokens in the ctx
            public long size;
            // the current token position
            long pos;
            // for objects only, the current field
            public Object field;

            public Ctx(Ctx prev, Token token, long size)
            {
                this.prev = prev;
                this.token = token;
                this.size = size;
                // we always increment before using, so start at -1
                this.pos = -1;
            }
        }

        private final Config config;
        // structure to decompress the long values back to fields
        private final FlexDict fields;

        public Ctx ctx;
        public Primitive primitive;
        public Object value;
        private boolean readingValue;
        private boolean end;

        public Decoder(Config config)
        {
            this.config = config;
            this.fields = new FlexDict(this.config.staticFieldsRev());

            this.ctx = null;
            this.primitive = null;
            this.value = null;
            this.readingValue = false;
            this.end = false;
        }

        private static final Object END = new Object();
        public Object read(BitReader base62Decoder, Compressor compressor)
        {
            Token token = this.readNext(base62Decoder, compressor);
            if (token == null) {
                return END;
            }

            switch (token) {
                case START_OBJECT:
                    Map<Object, Object> obj = new LinkedHashMap<>();
                    Object next;
                    while ((next = this.read(base62Decoder, compressor)) != END) {
                        obj.put(next, this.read(base62Decoder, compressor));
                    }
                    return obj;

                case START_ARRAY:
                    List<Object> arr = new ArrayList<>();
                    Object el;
                    while ((el = this.read(base62Decoder, compressor)) != END) {
                        arr.add(el);
                    }
                    return arr;

                case END_OBJECT:
                case END_ARRAY:
                    return END;

                default:
                    return this.value;
            }
        }

        public Token readNext(BitReader inputStream, Compressor compressor)
        {
            if (!this.readingValue) {
                if (this.ctx != null) {
                    Token t = this.ctx.token;
                    if (t.equals(Token.START_OBJECT) || t.equals(Token.START_ARRAY)) {
                        this.ctx.pos++;
                        if (this.ctx.pos == this.ctx.size) {
                            // pop the context stack
                            this.ctx = this.ctx.prev;
                            // if we move from a context to no context, we reached the end and the next call
                            // should return null
                            this.end = this.ctx == null;
                            // little trick to return the reverse of the start token
                            return t.rev;
                        }
                    }
                }
                else {
                    if (this.end) {
                        return null;
                    }
                }
            }

            int initialByte = this.readUint8(inputStream);
            int majorType = initialByte >>> 5;
            int additionalInfo = initialByte & 0x1f;
            long length = this.readLength(inputStream, additionalInfo);

            Token retVal;

            // I think this makes sense
            this.value = null;
            this.primitive = null;

            switch (majorType) {
                // 0: Unsigned integer
                case 0:
                    this.value = length;
                    this.primitive = Primitive.POSITIVE_INTEGER;
                    retVal = Token.VALUE_NUMBER_INT;
                    break;
                // 1: Negative integer
                case 1:
                    this.value = -1L - length;
                    this.primitive = Primitive.NEGATIVE_INTEGER;
                    retVal = Token.VALUE_NUMBER_INT;
                    break;
                // 2: Byte string
                case 2:
                    byte[] bytes = new byte[(int) length];
                    for (int i = 0; i < bytes.length; i++) {
                        bytes[i] = (byte) inputStream.read(8);
                    }
                    this.value = bytes;
                    this.primitive = Primitive.BYTE_STRING;
                    // this is a bit o a special one, but I guess in the end, we're an array of bytes, no?
                    retVal = Token.START_ARRAY;
                    break;
                // 3: Text string (UTF-8)
                case 3:
                    this.value = this.readString(inputStream, compressor, length);
                    this.primitive = Primitive.TEXT_STRING;
                    retVal = Token.VALUE_STRING;
                    break;
                // 4: Array of data items
                case 4:
                    this.ctx = new Ctx(this.ctx, Token.START_ARRAY, length);
                    retVal = Token.START_ARRAY;
                    break;
                // 5: Map of pairs of data items
                case 5:
                    // note that we count the tokens, so x2 to include the fields
                    this.ctx = new Ctx(this.ctx, Token.START_OBJECT, length * 2);
                    retVal = Token.START_OBJECT;
                    break;
                // 6: Tagged data items
                case 6:
                    // Tag 2: Positive bignum
                    // Tag 3: Negative bignum
                    switch (additionalInfo) {
                        case 2:
                        case 3:
                            boolean positive = additionalInfo == 2;
                            // this must be enabled to skip the context position increment because
                            // we're about to recurse and not fully done reading the current value yet
                            this.readingValue = true;
                            if (this.readNext(inputStream, compressor).equals(Token.START_ARRAY)) {
                                this.value = positive ? this.fromPositiveBigNum((byte[]) this.value)
                                                      : -1 - this.fromPositiveBigNum((byte[]) this.value);
                                this.primitive = positive ? Primitive.POSITIVE_BIGNUM : Primitive.NEGATIVE_BIGNUM;
                                this.readingValue = false;
                                retVal = Token.VALUE_NUMBER_INT;
                            }
                            else {
                                throw new IllegalStateException("Encountered bignum, but the next token isn't a byte array");
                            }
                            break;
                        default:
                            throw new IllegalStateException("Unimplemented tag value; " + additionalInfo);
                    }
                    break;
                // 7: Floating-point numbers and simple values (true/false/null/undefined)
                case 7:
                    switch (additionalInfo) {
                        // 20: False
                        case 20:
                            this.value = false;
                            this.primitive = Primitive.BOOLEAN;
                            retVal = Token.VALUE_FALSE;
                            break;
                        // 21: True
                        case 21:
                            this.value = true;
                            this.primitive = Primitive.BOOLEAN;
                            retVal = Token.VALUE_TRUE;
                            break;
                        // 22: Null
                        case 22:
                            this.value = null;
                            this.primitive = Primitive.NULL;
                            retVal = Token.VALUE_NULL;
                            break;
                        // 23: Undefined in Javascript
                        case 23:
                            this.value = null;
                            this.primitive = Primitive.UNDEFINED;
                            retVal = Token.VALUE_NULL;
                            break;
                        // 24: Simple value (8-bit)
                        case 24:
                            this.value = this.readUint8(inputStream);
                            this.primitive = Primitive.BYTE;
                            retVal = Token.VALUE_NUMBER_INT;
                            break;
                        // 25: Half-precision float (16-bit)
                        case 25:
                            throw new IllegalStateException("Half-precision (16-bit) float is not implemented because the encoder doesn't support it");
                            //this.value = this.readFloat16(length);
                            //this.primitive = Primitive.FLOAT16;
                            //retVal = Token.VALUE_NUMBER_FLOAT;
                            // 26: Single-precision float (32-bit)
                        case 26:
                            this.value = Float.intBitsToFloat((int) length);
                            this.primitive = Primitive.FLOAT32;
                            retVal = Token.VALUE_NUMBER_FLOAT;
                            break;
                        // 27: Double-precision float (64-bit)
                        case 27:
                            this.value = Double.longBitsToDouble(length);
                            this.primitive = Primitive.FLOAT64;
                            retVal = Token.VALUE_NUMBER_FLOAT;
                            break;
                        // 28-30: Reserved
                        // 31: Break (used for indefinite length items)
                        default:
                            throw new IllegalStateException("Unimplemented floating-point number or simple value; " + additionalInfo);
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown major type: " + majorType);
            }

            // special case: even tokens inside an object context should be returned as fields
            if (this.ctx != null && this.ctx.token.equals(Token.START_OBJECT) && this.ctx.pos % 2 == 0) {
                // not entirely sure what to put here, but I guess a field is not a primitive, right?
                this.primitive = null;
                this.value = this.parseField(this.value);
                // cache the field so we can query it during the next value call
                this.ctx.field = this.value;
                retVal = Token.FIELD_NAME;
            }

            return retVal;
        }

        private long readLength(BitReader inputStream, int additionalInfo)
        {
            // any value < 24 has the length value encoded into the 5 LSB of the majorType
            if (additionalInfo < 24) {
                return additionalInfo;
            }
            switch (additionalInfo) {
                // 24 (0x18): Next 1 byte holds value
                case 24:
                    return readUint8(inputStream);
                // 25 (0x19): Next 2 bytes hold value
                case 25:
                    return this.readUint16(inputStream);
                // 26 (0x1A): Next 4 bytes hold value
                case 26:
                    return this.readUint32(inputStream);
                // 27 (0x1B): Next 8 bytes hold value
                case 27:
                    return this.readUint64(inputStream);
                // 28-30: Reserved
                // 31 (0x1F): Special meaning (like indefinite length for types 2-5, "break" for type 7)
                default:
                    throw new IllegalStateException("Invalid additional info: " + additionalInfo);
            }
        }
        private String readString(BitReader inputStream, Compressor compressor, long length)
        {
            // note that, for now, the encoder doesn't support indefinite lengths (length -1, see cbor.js),
            // so we don't either
            if (this.config.enableStringCompression() && compressor != null && length > 0) {
                // the wrapper will read the inputStream for max length bytes
                return compressor.decompress(new WrappedByteReader(inputStream, length));
            }
            else {
                byte[] stringBytes = new byte[(int) length];
                for (int i = 0; i < stringBytes.length; i++) {
                    stringBytes[i] = (byte) inputStream.read(8);
                }
                // note that cbor strings are UTF-8 encoded
                return new String(stringBytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        private int readUint8(BitReader inputStream)
        {
            return inputStream.read(8) & 0xFF;
        }
        private int readUint16(BitReader inputStream)
        {
            return inputStream.read(16) & 0xFFFF;
        }
        // make sure to return a long because in Java, an int has max 31 bits (+ 1 sign bit)
        private long readUint32(BitReader inputStream)
        {
            // let's keep these long too so the calc below doesn't overflow
            long high = this.readUint16(inputStream);
            long low = this.readUint16(inputStream);
            return (high * POW_2_16) + low;
        }
        private long readUint64(BitReader inputStream)
        {
            // sync with encoder
            long high = inputStream.read(32);
            long low = inputStream.read(32);
            return (high * POW_2_32) + low;
        }
        // Commented because the encoder doesn't use it (float is always 32 bit in Java)
        //        private double readFloat16(BitReader inputStream)
        //        {
        //            long value = this.readUint16(inputStream);
        //            int sign = value & 0x8000;
        //            int exponent = value & 0x7c00;
        //            int fraction = value & 0x03ff;
        //
        //            if (exponent == 0x7c00) {
        //                exponent = 0xff << 10;
        //            }
        //            else if (exponent != 0) {
        //                exponent += (127 - 15) << 10;
        //            }
        //            else if (fraction != 0) {
        //                return (sign != 0 ? -1 : 1) * fraction * POW_2_24;
        //            }
        //
        //            int bits = sign << 16 | exponent << 13 | fraction << 13;
        //            return Float.intBitsToFloat(bits);
        //        }
        private long fromPositiveBigNum(byte[] bytes)
        {
            long result = 0;

            for (byte b : bytes) {
                result = (result << 8) | (b & 0xFF);
            }

            return result;
        }
        private Object parseField(Object nameOrIdx)
        {
            if (this.config.enableKeyMapping()) {
                if (nameOrIdx instanceof String) {
                    this.fields.add(this.fields.size(), nameOrIdx);
                }
                else if (nameOrIdx instanceof Number keyNum) {
                    // note that the encoded field number is always returned as a long,
                    // so make sure to use the int value or this will return null
                    nameOrIdx = this.fields.get(keyNum.intValue());
                }
                else {
                    throw new IllegalArgumentException("Invalid key type: " + nameOrIdx);
                }
            }

            // note that, in cbor, a field doesn't have to be a string,
            // so let's return object
            return nameOrIdx;
        }
    }
}
