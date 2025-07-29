/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

/**
 * Created by bram on 7/28/15.
 */
base.plugin("base.core.Bbor62", ["constants.base.core", "base.core.Class", function (BaseConstants, Class)
{
    // ----- ByteStream.java -----
    class ByteStream
    {
        constructor()
        {
            // use Uint8Array instead of byte[] for more efficient byte storage
            this.buffer = new Uint8Array(8);
            this.readPos = 0;
            this.readBitPos = 0;
            this.writePos = 0;
            this.writeBitPos = 0;
            this.writeBuf = 0;
        }

        read(numBits)
        {
            if (numBits <= 0 || numBits > 32) {
                throw new Error("Number of bits must be between 1 and 32");
            }

            var result = 0;
            var bitsRemaining = numBits;

            while (bitsRemaining > 0) {
                var currentValue = this.buffer[this.readPos];
                var bitsAvailable = 8 - this.readBitPos;
                var bitsToRead = Math.min(bitsRemaining, bitsAvailable);
                var mask = (1 << bitsToRead) - 1;
                var shift = bitsAvailable - bitsToRead;
                var extractedBits = (currentValue >>> shift) & mask;

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

        hasNext(numBits)
        {
            return (this.readPos * 8 + this.readBitPos + numBits) <= this.writePos * 8;
        }

        write(value, numBits)
        {
            if (numBits <= 0 || numBits > 32) {
                throw new Error("Number of bits must be between 1 and 32");
            }
            if (value >= (1 << numBits)) {
                throw new Error("Value is too large for the specified number of bits");
            }

            while (numBits > 0) {
                var bitsAvailable = 8 - this.writeBitPos;
                var bitsToWrite = Math.min(numBits, bitsAvailable);
                var remainingBits = numBits - bitsToWrite;

                var mask = (1 << bitsToWrite) - 1;
                var val = (value >>> remainingBits) & mask;

                this.writeBuf = (this.writeBuf << bitsToWrite) | val;
                this.writeBitPos += bitsToWrite;

                if (this.writeBitPos === 8) {
                    this._assertSpaceFor(1);
                    this.buffer[this.writePos++] = this.writeBuf;
                    this.writeBuf = 0;
                    this.writeBitPos = 0;
                }

                numBits = remainingBits;
            }
        }

        flush()
        {
            if (this.writeBitPos > 0) {
                throw new Error("Remaining bits left in the byte stream, this shouldn't happen; " + this.writeBitPos);
            }
        }

        length()
        {
            return this.writePos;
        }

        _assertSpaceFor(numBytes)
        {
            if (this.readPos > 0) {
                var newBuffer = new Uint8Array(this.buffer.length);
                // JS for arraycopy
                newBuffer.set(this.buffer.subarray(this.readPos, this.buffer.length), 0);
                this.buffer = newBuffer;
                this.writePos -= this.readPos;
                this.readPos = 0;
            }

            if (this.writePos + numBytes > this.buffer.length) {
                var newBuffer = new Uint8Array(this.buffer.length * 2);
                newBuffer.set(this.buffer);
                this.buffer = newBuffer;
            }
        }
    }

    // ----- WrappedByteReader.java -----
    class WrappedByteReader
    {
        constructor(stream, byteNum)
        {
            this.stream = stream;
            this.byteNum = byteNum;
            this.bitsRead = 0;
            this.bytesRead = 0;
        }

        read(numBits)
        {
            var retVal = this.stream.read(numBits);

            this.bitsRead += numBits;
            // note that in JS, Math.floor() is the same as Math.floorDiv()
            this.bytesRead += Math.floor(this.bitsRead / 8);
            this.bitsRead = this.bitsRead % 8;

            return retVal;
        }

        hasNext(numBits)
        {
            var newBitsRead = this.bitsRead + numBits;
            // note that in JS, Math.floor() is the same as Math.floorDiv()
            var newBytesRead = this.bytesRead + Math.floor(newBitsRead / 8);
            newBitsRead = newBitsRead % 8;

            return newBytesRead < this.byteNum || (newBytesRead === this.byteNum && newBitsRead === 0);
        }
    }

    // ----- FlexDict.java -----
    class FlexDict
    {
        // Note: used Number.MAX_SAFE_INTEGER instead of Integer.MAX_VALUE
        constructor(staticDict, enableDynamic = true, maxSize = Number.MAX_SAFE_INTEGER)
        {
            this.staticDict = staticDict;
            // note that we use a Map, because plain objects (eg. {}):
            // - didn't guarantee property order before ES2015 (now they do for string keys)
            // - don't keep size
            // - behave differently for string keys as for int keys
            this.dynamicDict = new Map();
            this.enableDynamic = enableDynamic;
            this.maxSize = maxSize;
        }

        get(key)
        {
            return this.staticDict.has(key) ? this.staticDict.get(key) : this.dynamicDict.get(key);
        }

        hasKey(key)
        {
            return this.staticDict.has(key) || this.dynamicDict.has(key);
        }

        add(key, val)
        {
            if (this.enableDynamic && this.size() < this.maxSize && !this.hasKey(key)) {
                this.dynamicDict.set(key, val);
            }
        }

        size()
        {
            return this.staticDict.size + this.dynamicDict.size;
        }

        reset()
        {
            this.dynamicDict.clear();
        }
    }

    // ----- BaseXStream.java -----
    class BaseXStream
    {
        // Note that this is a static getter property
        // (methods that look like properties but actually execute code when accessed)
        static get BITS_PER_BYTE()
        {
            return 8;
        }

        static log2ceil(x)
        {
            return 32 - Math.clz32(x - 1);
        }
    }

    class BaseXStreamEncoder
    {
        constructor(consumer, config)
        {
            this.consumer = consumer || function ()
            {
            };
            this.config = config || BaseXStream.DEFAULT_CONFIG;
            this.buffer = 0;
            this.bitsInBuffer = 0;
            this.byteModulo = 0;
        }

        write(value, numBits)
        {
            if (numBits <= 0 || numBits > 32) {
                throw new Error("Number of bits must be between 1 and 32");
            }
            // note that 1 << numBits returns 1, because bits shifts in JS only have 32 bit precision
            if (numBits === 32) {
                if (value >= 4294967296) {
                    throw new Error("Value is too large for the specified number of bits");
                }
            }
            // numBits is guaranteed < 32
            else if (value >= (1 << numBits)) {
                throw new Error("Value is too large for the specified number of bits");
            }

            while (numBits > 0) {
                var bitsAvailable = this.config.bitsPerBlock - this.bitsInBuffer;
                var bitsToWrite = Math.min(numBits, bitsAvailable);
                var remainingBits = numBits - bitsToWrite;

                var mask = (1 << bitsToWrite) - 1;
                var val = (value >>> remainingBits) & mask;

                this.buffer = (this.buffer << bitsToWrite) | val;
                this.bitsInBuffer += bitsToWrite;
                this.byteModulo = (this.byteModulo + bitsToWrite) % BaseXStream.BITS_PER_BYTE;

                if (this.bitsInBuffer === this.config.bitsPerBlock) {
                    if (this.config.enableBitSqueezing) {
                        while (remainingBits > 0) {
                            var extraBitVal = (value >>> (remainingBits - 1)) & 1;
                            var tryBuffer = (this.buffer << 1) | extraBitVal;

                            if (tryBuffer > this.config.maxBlockValue && tryBuffer < this.config.maxBlockCapacity) {
                                this.buffer = tryBuffer;
                                this.bitsInBuffer++;
                                this.byteModulo = (this.byteModulo + 1) % BaseXStream.BITS_PER_BYTE;
                                remainingBits--;
                            }
                            else {
                                break;
                            }
                        }
                    }

                    this.consumer(this.encode(this.buffer, false));
                    this.buffer = 0;
                    this.bitsInBuffer = 0;
                }

                numBits = remainingBits;
            }
        }

        flush()
        {
            if (this.bitsInBuffer > 0) {
                if (this.byteModulo === 0) {
                    var mask = (1 << this.bitsInBuffer) - 1;
                    var valueToEncode = this.buffer & mask;
                    var lastCode = this.encode(valueToEncode, true);
                    this.consumer(lastCode);
                }
                else {
                    throw new Error("Stream is not byte aligned");
                }
            }

            this.buffer = 0;
            this.bitsInBuffer = 0;
        }

        encode(valueToEncode, finalBlock)
        {
            var result = '';

            do {
                var remainder = valueToEncode % this.config.radix;
                valueToEncode = Math.floor(valueToEncode / this.config.radix);
                result = this.config.dict.charAt(remainder) + result;
            } while (valueToEncode !== 0);

            if (!finalBlock) {
                while (result.length < this.config.charsPerBlock) {
                    result = this.config.dict.charAt(0) + result;
                }
            }
            else {
                while (BaseXStream.log2ceil(Math.pow(this.config.radix, result.length) - 1) < this.bitsInBuffer) {
                    result = this.config.dict.charAt(0) + result;
                }
            }

            return result;
        }
    }

    BaseXStream.Encoder = BaseXStreamEncoder;

    class BaseXStreamDecoder
    {
        constructor(input, config)
        {
            this.config = config || BaseXStream.DEFAULT_CONFIG;
            this.buffer = 0;
            this.bitsInBuffer = 0;
            this.byteModulo = 0;
            this.currentBlock = '';
            this.blockVal = 0;
            this.blockPos = this.config.charsPerBlock - 1;
            this.finalBlockLut = this.buildFinalBlockLut();

            this.inputPos = 0;
            this.readBuffer = new ByteStream();
            this.input = input;
        }

        read(numBits)
        {
            return this.assertBits(numBits).read(numBits);
        }

        hasNext(numBits)
        {
            return this.readBuffer.hasNext(numBits) || this.inputPos < this.input.length;
        }

        assertBits(numBits)
        {
            while (!this.readBuffer.hasNext(numBits) && this.inputPos < this.input.length) {
                var lastChar = this.inputPos === this.input.length - 1;
                this.readChar(this.input.charAt(this.inputPos++), lastChar);
            }
            return this.readBuffer;
        }

        readChar(c, lastChar)
        {
            // note that dictRev is a simple object
            var index = this.config.dictRev[c];
            if (index === undefined) {
                throw new Error("Invalid character: " + c);
            }

            this.blockVal = this.blockVal + (index * Math.pow(this.config.radix, this.blockPos--));
            this.currentBlock += c;

            if (this.blockPos < 0) {
                var bitsInBlock = lastChar ? this.getLastBlockBitLength() : this.config.bitsPerBlock;
                this.decodeBlock(bitsInBlock);

                this.currentBlock = '';
                this.blockVal = 0;
                this.blockPos = this.config.charsPerBlock - 1;
            }

            if (lastChar) {
                if (this.currentBlock.length > 0) {
                    this.blockVal = 0;
                    this.blockPos = this.currentBlock.length - 1;

                    for (var i = 0; i < this.currentBlock.length; i++) {
                        this.blockVal = this.blockVal + (this.config.dictRev[this.currentBlock.charAt(i)] * Math.pow(this.config.radix, this.blockPos--));
                    }

                    var lastBlockBits = this.getLastBlockBitLength();
                    this.decodeBlock(lastBlockBits);
                }

                if (this.bitsInBuffer > 0) {
                    throw new Error("Bitstream is not byte aligned");
                }

                this.buffer = 0;
                this.bitsInBuffer = 0;
                this.byteModulo = 0;
            }
        }

        decodeBlock(numBits)
        {
            var extraBits = 0;
            while (this.blockVal >>> extraBits > this.config.maxBlockValue) {
                numBits++;
                extraBits++;
            }

            var mask = (1 << numBits) - 1;
            this.buffer = (this.buffer << numBits) | (this.blockVal & mask);
            this.bitsInBuffer += numBits;
            this.byteModulo = (this.byteModulo + numBits) % BaseXStream.BITS_PER_BYTE;

            while (this.bitsInBuffer >= BaseXStream.BITS_PER_BYTE) {
                var bitsNotToDecode = this.bitsInBuffer - BaseXStream.BITS_PER_BYTE;
                var valueToDecode = this.buffer >>> bitsNotToDecode;
                this.readBuffer.write(valueToDecode, BaseXStream.BITS_PER_BYTE);
                var m = (1 << bitsNotToDecode) - 1;
                this.buffer &= m;
                this.bitsInBuffer = bitsNotToDecode;
            }
        }

        getLastBlockBitLength()
        {
            if (this.byteModulo === 0 && this.currentBlock.length === this.config.charsPerBlock) {
                return this.config.bitsPerBlock;
            }
            else {
                // see buildFinalBlockLut() notes
                var entry = this.byteModulo + ',' + this.currentBlock.length;
                if (this.finalBlockLut.has(entry)) {
                    return this.finalBlockLut.get(entry);
                }
                else {
                    throw new Error("Invalid last block combination: startPos=" + this.byteModulo + ", numChars=" + this.currentBlock.length);
                }
            }
        }

        buildFinalBlockLut()
        {
            var retVal = new Map();

            for (var numChars = 1; numChars <= this.config.charsPerBlock; numChars++) {
                var minValue = Math.pow(this.config.radix, numChars - 1);
                var minBits = Math.max(BaseXStream.log2ceil(minValue), 1);
                var maxValue = Math.pow(this.config.radix, numChars) - 1;
                var maxBits = Math.min(BaseXStream.log2ceil(maxValue), this.config.bitsPerBlock);

                for (var modulo = 0; modulo < BaseXStream.BITS_PER_BYTE; modulo++) {
                    for (var bitLength = minBits; bitLength <= maxBits; bitLength++) {
                        if ((modulo + bitLength) % BaseXStream.BITS_PER_BYTE === 0) {
                            // watch out: JS maps test equality by reference, not value, so we can't use an array pair here
                            // instead, let's convert to a string
                            var startState = modulo + ',' + numChars;
                            if (!retVal.has(startState)) {
                                retVal.set(startState, bitLength);
                            }
                            else {
                                throw new Error("Configuration cannot guarantee unique final block bit length");
                            }
                        }
                    }
                }
            }

            return retVal;
        }
    }

    BaseXStream.Decoder = BaseXStreamDecoder;

    BaseXStream.DEFAULT_CONFIG = {
        // note that this is a string
        dict: BaseConstants.BASE62_ALPHABET,
        charsPerBlock: 4,
        enableBitSqueezing: true,
        // and this is an object
        dictRev: null,
        radix: null,
        bitsPerBlock: null,
        maxBlockValue: null,
        maxBlockCapacity: null,

        init()
        {
            this.dictRev = {};
            for (var i = 0; i < this.dict.length; i++) {
                this.dictRev[this.dict.charAt(i)] = i;
            }

            this.radix = this.dict.length;
            var bitsPerChar = Math.log(this.radix) / Math.log(2);
            this.bitsPerBlock = Math.floor(bitsPerChar * this.charsPerBlock);
            this.maxBlockValue = Math.pow(2, this.bitsPerBlock) - 1;
            this.maxBlockCapacity = Math.pow(this.radix, this.charsPerBlock);
        }
    };
    BaseXStream.DEFAULT_CONFIG.init();

    // ----- LZW.java -----
    class LZW
    {
        static get ASCII_ESCAPE_IDX()
        {
            return 0;
        }

        static get ASCII_ESCAPE_VAL()
        {
            return String.fromCharCode(LZW.ASCII_ESCAPE_IDX);
        }

        static get UNICODE_ESCAPE_IDX()
        {
            return 1;
        }

        static get UNICODE_ESCAPE_VAL()
        {
            return String.fromCharCode(LZW.UNICODE_ESCAPE_IDX);
        }

        constructor(config)
        {
            this.config = config || LZW.DEFAULT_CONFIG;
            this.encodeDict = null;
            this.decodeDict = null;
        }

        compress(input, output)
        {
            if (!input || input.length === 0) {
                throw new Error("Please provide input");
            }

            if (this.encodeDict === null) {
                this.encodeDict = new FlexDict(this.config.staticDict, this.config.enableDynamicDict, this.config.maxDictSize);
            }
            var ctx = new LZW.Ctx(this.encodeDict);

            var last = input.charAt(0);
            for (var i = 1; i < input.length; i++) {
                var current = input.charAt(i);
                var lastPlusCurrent = last + current;

                if (ctx.dict.hasKey(lastPlusCurrent)) {
                    last = lastPlusCurrent;
                }
                else {
                    var isNew = this.write(output, ctx, last);
                    this.checkReset(ctx.dict);

                    if (isNew) {
                        ctx.dict.add(last, ctx.dict.size());
                    }

                    ctx.dict.add(lastPlusCurrent, ctx.dict.size());
                    last = current;
                }
            }

            var isNew = this.write(output, ctx, last);
            this.checkReset(ctx.dict);

            if (isNew) {
                ctx.dict.add(last, ctx.dict.size());
            }

            if (this.config.byteAlignMode && ctx.byteAlignVal > 0) {
                var bitsUntilFullByte = 8 - ctx.byteAlignVal;
                var bitsNeededForSeq = this.bitsNeeded(ctx.dict.size() - 1);
                var bitsToWrite = Math.min(bitsNeededForSeq, bitsUntilFullByte);
                output.write(LZW.ASCII_ESCAPE_IDX, bitsToWrite);
                bitsUntilFullByte -= bitsToWrite;

                if (bitsUntilFullByte > 0) {
                    output.write(0, bitsUntilFullByte);
                }
                ctx.byteAlignVal = 0;
            }
        }

        decompress(input)
        {
            var retVal = '';

            if (this.decodeDict === null) {
                this.decodeDict = new FlexDict(this.config.staticDictRev, this.config.enableDynamicDict, this.config.maxDictSize);
            }
            var ctx = new LZW.Ctx(this.decodeDict);

            var nextIndex = ctx.dict.size() - 1;
            var last = null;
            var current;

            while ((current = this.read(input, ctx, nextIndex, last)) !== null) {
                retVal += current.value;

                if (last !== null) {
                    ctx.dict.add(ctx.dict.size(), last + current.value.charAt(0));
                }

                this.checkReset(ctx.dict);

                if (current.isNew) {
                    ctx.dict.add(ctx.dict.size(), current.value);
                }

                last = current.value;

                if (this.config.enableDynamicDict) {
                    nextIndex = (ctx.dict.size() - 1) + 1;
                }
            }

            return retVal;
        }

        write(output, ctx, value)
        {
            var isNew = false;
            var code = ctx.dict.get(value);
            var bitsNeeded = this.bitsNeeded(ctx.dict.size() - 1);

            if (code !== undefined) {
                if (code === LZW.ASCII_ESCAPE_IDX || code === LZW.UNICODE_ESCAPE_IDX) {
                    output.write(LZW.ASCII_ESCAPE_IDX, bitsNeeded);
                    output.write(code, 8);
                }
                else {
                    output.write(code, bitsNeeded);
                }
            }
            else {
                if (value.length === 1) {
                    var c = value.charCodeAt(0);

                    if (c < 256) {
                        output.write(LZW.ASCII_ESCAPE_IDX, bitsNeeded);
                        output.write(c, 8);
                    }
                    else {
                        output.write(LZW.UNICODE_ESCAPE_IDX, bitsNeeded);
                        output.write(c, 16);
                    }

                    isNew = true;
                }
                else {
                    throw new Error("This shouldn't happen, all new values > 1 should be in the dict; " + value);
                }
            }

            ctx.byteAlignVal = (ctx.byteAlignVal + bitsNeeded) % 8;

            return isNew;
        }

        read(input, ctx, nextIndex, last)
        {
            var retVal = null;
            var bitsNeeded = this.bitsNeeded(nextIndex);

            if (input.hasNext(bitsNeeded)) {
                var code = input.read(bitsNeeded);
                ctx.byteAlignVal = (ctx.byteAlignVal + bitsNeeded) % 8;

                retVal = new LZW.Token();
                if (code === LZW.ASCII_ESCAPE_IDX || code === LZW.UNICODE_ESCAPE_IDX) {
                    if (this.config.byteAlignMode && !input.hasNext(8)) {
                        if (ctx.byteAlignVal > 0) {
                            input.read(8 - ctx.byteAlignVal);
                        }
                        retVal = null;
                    }
                    else {
                        // Using String.fromCharCode instead of String.valueOf for character conversion
                        retVal.value = String.fromCharCode(input.read(code === LZW.ASCII_ESCAPE_IDX ? 8 : 16));
                        retVal.isNew = retVal.value !== LZW.ASCII_ESCAPE_VAL && retVal.value !== LZW.UNICODE_ESCAPE_VAL;
                    }
                }
                else if (ctx.dict.hasKey(code)) {
                    retVal.value = ctx.dict.get(code);
                }
                else if (code === nextIndex) {
                    retVal.value = last + last.charAt(0);
                }
                else {
                    throw new Error("Invalid compressed data");
                }
            }
            else {
                if (this.config.byteAlignMode && ctx.byteAlignVal > 0) {
                    input.read(8 - ctx.byteAlignVal);
                }
            }

            return retVal;
        }

        checkReset(dict)
        {
            if (this.config.enableDynamicDict && this.config.enableDictReset && dict.size() >= this.config.maxDictSize) {
                dict.reset();
            }
        }

        bitsNeeded(code)
        {
            return code === 0 ? 1 : 32 - Math.clz32(code);
        }
    }

    class LZWToken
    {
        constructor()
        {
            this.value = null;
            this.isNew = false;
        }
    }

    LZW.Token = LZWToken;

    class LZWCtx
    {
        constructor(dict)
        {
            this.dict = dict;
            this.byteAlignVal = 0;
        }
    }

    LZW.Ctx = LZWCtx;

    LZW.DEFAULT_CONFIG = {
        INITIAL_DICT: [
            LZW.ASCII_ESCAPE_VAL,
            LZW.UNICODE_ESCAPE_VAL,
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            " ", ".", ",",
            "e", "a", "r", "i", "o", "t", "n", "s", "l", "c",
            "u", "d", "p", "m", "h", "g", "b", "f", "v", "k",
            "w", "j", "q", "x", "y", "z",
            "A", "M", "S", "C", "P", "D", "B", "R", "L", "T",
            "E", "N", "H", "G", "F", "W", "I", "J", "K", "O",
            "V", "U", "Q", "X", "Y", "Z",
            "th", "en", "er", "in", "es", "on", "an", "re", "st", "le"
        ],

        enableDynamicDict: true,
        maxDictSize: Math.pow(2, 10) - 1,
        enableDictReset: true,
        byteAlignMode: true,
        staticDict: new Map(),
        staticDictRev: new Map(),

        init()
        {
            for (var i = 0; i < this.INITIAL_DICT.length; i++) {
                var c = this.INITIAL_DICT[i];
                if (!this.staticDict.has(c)) {
                    this.staticDict.set(c, i);
                    this.staticDictRev.set(i, c);
                }
                else {
                    throw new Error("Duplicate dict character detected '" + c + "'");
                }
            }
        },
    };
    LZW.DEFAULT_CONFIG.init();

    // ----- Bbor.java -----
    class Bbor
    {
        static get POW_2_16()
        {
            return Math.pow(2, 16);
        }

        static get POW_2_32()
        {
            return Math.pow(2, 32);
        }

        static get JS_MAX_SAFE_INTEGER()
        {
            return Number.MAX_SAFE_INTEGER;
        }
    }

    Bbor.Token = {
        END_OBJECT: 'END_OBJECT',
        START_OBJECT: 'START_OBJECT',
        END_ARRAY: 'END_ARRAY',
        START_ARRAY: 'START_ARRAY',
        FIELD_NAME: 'FIELD_NAME',
        VALUE_STRING: 'VALUE_STRING',
        VALUE_NUMBER_INT: 'VALUE_NUMBER_INT',
        VALUE_NUMBER_FLOAT: 'VALUE_NUMBER_FLOAT',
        VALUE_TRUE: 'VALUE_TRUE',
        VALUE_FALSE: 'VALUE_FALSE',
        VALUE_NULL: 'VALUE_NULL'
    };

    Bbor.TokenRev = {
        START_OBJECT: Bbor.Token.END_OBJECT,
        START_ARRAY: Bbor.Token.END_ARRAY
    };

    Bbor.Primitive = {
        POSITIVE_INTEGER: 'POSITIVE_INTEGER',
        NEGATIVE_INTEGER: 'NEGATIVE_INTEGER',
        BYTE_STRING: 'BYTE_STRING',
        TEXT_STRING: 'TEXT_STRING',
        POSITIVE_BIGNUM: 'POSITIVE_BIGNUM',
        NEGATIVE_BIGNUM: 'NEGATIVE_BIGNUM',
        BOOLEAN: 'BOOLEAN',
        NULL: 'NULL',
        UNDEFINED: 'UNDEFINED',
        BYTE: 'BYTE',
        FLOAT16: 'FLOAT16',
        FLOAT32: 'FLOAT32',
        FLOAT64: 'FLOAT64'
    };

    class BborCtx
    {
        constructor(prev, token, size)
        {
            this.prev = prev;
            this.token = token;
            this.size = size;
            this.pos = -1;
            this.field = null;
        }
    }

    Bbor.Ctx = BborCtx;

    class BborEncoder
    {
        constructor(config)
        {
            this.config = config || Bbor.DEFAULT_CONFIG;
            this.fields = new FlexDict(this.config.staticFields);
        }

        write(outputStream, compressor, value)
        {
            if (value === null) {
                return this.writeNull(outputStream);
            }

            if (value === undefined) {
                return this.writeUndefined(outputStream);
            }

            if (typeof value === 'boolean') {
                return this.writeBoolean(outputStream, value);
            }

            if (typeof value === 'number') {
                return this.writeNumber(outputStream, value);
            }

            if (typeof value === 'string') {
                return this.writeString(outputStream, compressor, value);
            }

            if (value instanceof Uint8Array) {
                return this.writeBytes(outputStream, value);
            }

            if (Array.isArray(value)) {
                this.writeStartArray(outputStream, value.length);
                for (var i = 0; i < value.length; i++) {
                    this.write(outputStream, compressor, value[i]);
                }
                this.writeEndArray(outputStream);
                return this;
            }

            if (typeof value === 'object') {
                var keys = Object.keys(value);
                this.writeStartObject(outputStream, keys.length);
                for (var i = 0; i < keys.length; i++) {
                    this.writeFieldName(outputStream, compressor, keys[i]);
                    this.write(outputStream, compressor, value[keys[i]]);
                }
                this.writeEndObject(outputStream);
                return this;
            }

            if (typeof value === 'function') {
                // update 2990725: decided to add this as a NOOP
                // so we don't need to add logic to skip the functions
                // in some exotic objects (like fullcalendar events),
                // but know they won't get serialized (could be a future todo or config setting)!
                return this;
            }

            throw new Error("Unsupported type: " + typeof value);
        }

        writeNull(outputStream)
        {
            this.writeUint8(outputStream, 0xf6);
            return this;
        }

        writeUndefined(outputStream)
        {
            this.writeUint8(outputStream, 0xf7);
            return this;
        }

        writeBoolean(outputStream, value)
        {
            this.writeUint8(outputStream, value ? 0xf5 : 0xf4);
            return this;
        }

        /**
         * Watch out: JS has only a single numeric type: Number,
         * which is a double-precision 64-bit floating point number (IEEE 754)
         * All numbers are stored as 64-bit floating point numbers (doubles).
         * Note that a side effect of this Java port is that the toPositiveBigNum() below
         * will never trigger, since we already checked for <= Number.MAX_SAFE_INTEGER.
         */
        writeNumber(outputStream, value)
        {
            if (Math.floor(value) === value && Math.abs(value) <= Number.MAX_SAFE_INTEGER) {
                if (value >= 0) {
                    if (value <= Bbor.JS_MAX_SAFE_INTEGER) {
                        this.writeTypeAndLength(outputStream, 0, value);
                        return this;
                    }
                    // should never trigger
                    else {
                        this.writeUint8(outputStream, 0xc2);
                        var bytes = this.toPositiveBigNum(value);
                        this.writeTypeAndLength(outputStream, 2, bytes.length);
                        for (var i = 0; i < bytes.length; i++) {
                            this.writeUint8(outputStream, bytes[i]);
                        }
                        return this;
                    }
                }
                else {
                    if (-Bbor.JS_MAX_SAFE_INTEGER <= value) {
                        this.writeTypeAndLength(outputStream, 1, -1 - value);
                        return this;
                    }
                    // should never trigger
                    else {
                        this.writeUint8(outputStream, 0xc3);
                        var bytes = this.toPositiveBigNum(-1 - value);
                        this.writeTypeAndLength(outputStream, 2, bytes.length);
                        for (var i = 0; i < bytes.length; i++) {
                            this.writeUint8(outputStream, bytes[i]);
                        }
                        return this;
                    }
                }
            }

            // little trick to reduce the precision of a decimal to 32-bits
            var float32 = new Float32Array([value])[0];
            if (Math.abs((value - float32) / value) < 1e-7) {
                this.writeUint8(outputStream, 0xfa);
                this.writeFloat32(outputStream, value);
            }
            else {
                this.writeUint8(outputStream, 0xfb);
                this.writeFloat64(outputStream, value);
            }

            return this;
        }

        writeString(outputStream, compressor, value)
        {
            // in JS, strings are represented as 16-bit Unicode characters (UTF-16),
            // just like in Java, so nothing to change here when compression is enabled
            if (this.config.enableStringCompression && compressor && value.length > 0) {
                var byteStream = new ByteStream();
                compressor.compress(value, byteStream);
                byteStream.flush();
                this.writeTypeAndLength(outputStream, 3, byteStream.length());
                while (byteStream.hasNext(8)) {
                    this.writeUint8(outputStream, byteStream.read(8));
                }
                return this;
            }
            else {
                // equivalent of String.getBytes(), returns Uint8Array
                // (see original cbor.js for alternative, raw implementation)
                var utf8Data = new TextEncoder().encode(value);
                this.writeTypeAndLength(outputStream, 3, utf8Data.length);
                for (var i = 0; i < utf8Data.length; i++) {
                    this.writeUint8(outputStream, utf8Data[i]);
                }
                return this;
            }
        }

        writeBytes(outputStream, value)
        {
            this.writeTypeAndLength(outputStream, 2, value.length);
            for (var i = 0; i < value.length; i++) {
                this.writeUint8(outputStream, value[i]);
            }
            return this;
        }

        writeFieldName(outputStream, compressor, value)
        {
            if (this.config.enableKeyMapping) {
                if (!this.fields.hasKey(value)) {
                    this.writeString(outputStream, compressor, value);
                    this.fields.add(value, this.fields.size());
                }
                else {
                    this.writeNumber(outputStream, this.fields.get(value));
                }
            }
            else {
                this.writeString(outputStream, compressor, value);
            }
            return this;
        }

        writeStartArray(outputStream, size)
        {
            this.writeTypeAndLength(outputStream, 4, size);
            return this;
        }

        writeEndArray(outputStream)
        {
            return this;
        }

        writeStartObject(outputStream, size)
        {
            this.writeTypeAndLength(outputStream, 5, size);
            return this;
        }

        writeEndObject(outputStream)
        {
            return this;
        }

        writeTypeAndLength(outputStream, type, length)
        {
            if (length < 24) {
                this.writeUint8(outputStream, (type << 5) | length);
            }
            else if (length < 0x100) {
                this.writeUint8(outputStream, (type << 5) | 24);
                this.writeUint8(outputStream, length);
            }
            else if (length < 0x10000) {
                this.writeUint8(outputStream, (type << 5) | 25);
                this.writeUint16(outputStream, length);
            }
            else if (length < 0x100000000) {
                this.writeUint8(outputStream, (type << 5) | 26);
                this.writeUint32(outputStream, length);
            }
            else {
                this.writeUint8(outputStream, (type << 5) | 27);
                this.writeUint64(outputStream, length);
            }
        }

        writeUint8(outputStream, value)
        {
            outputStream.write(value, 8);
        }

        writeUint16(outputStream, value)
        {
            outputStream.write(value, 16);
        }

        writeUint32(outputStream, value)
        {
            outputStream.write(value, 32);
        }

        writeUint64(outputStream, value)
        {
            var low = value % Bbor.POW_2_32;
            var high = (value - low) / Bbor.POW_2_32;
            outputStream.write(high, 32);
            outputStream.write(low, 32);
        }

        writeFloat32(outputStream, value)
        {
            this.writeUint32(outputStream, this.floatToIntBits(value));
        }

        // this is a bit different from the Java implementation because we wanted to avoid BigInt
        // and return two 32-bit numbers instead
        writeFloat64(outputStream, value)
        {
            var bits = this.doubleToLongBits(value);
            outputStream.write(bits.high, 32);
            outputStream.write(bits.low, 32);
        }

        toPositiveBigNum(value)
        {
            var bytes = new Array(8);
            for (var i = 0; i < 8; i++) {
                bytes[7 - i] = value & 0xFF;
                value = value >>> 8;
            }

            var startIndex = 0;
            while (startIndex < 7 && bytes[startIndex] === 0) {
                startIndex++;
            }

            return bytes.slice(startIndex);
        }

        // JS port of Float.floatToIntBits()
        floatToIntBits(value)
        {
            // Create an ArrayBuffer with 4 bytes (32 bits)
            var buffer = new ArrayBuffer(4);
            // Create a DataView for binary manipulation
            var view = new DataView(buffer);
            // Store the float32 in the buffer
            view.setFloat32(0, value, false); // false = big-endian, as used in IEEE 754
            // Retrieve the 32-bit integer representation
            return view.getInt32(0, false); // false = big-endian
        }

        // JS port of Double.doubleToLongBits()
        doubleToLongBits(value)
        {
            // Create an ArrayBuffer with 8 bytes (64 bits)
            var buffer = new ArrayBuffer(8);
            // Create a DataView for binary manipulation
            var view = new DataView(buffer);
            // Store the float64 (double) in the buffer
            view.setFloat64(0, value, false); // false = big-endian, as used in IEEE 754
            // Retrieve the 64-bit representation as two 32-bit integers
            // since JavaScript doesn't natively support 64-bit integers,
            // and we want to avoid BigInt because too recent
            return {
                high: view.getUint32(0, false), // Top 32 bits
                low: view.getUint32(4, false) // Bottom 32 bits
            };
        }
    }
    Bbor.Encoder = BborEncoder;

    class BborDecoder
    {
        constructor(config)
        {
            this.config = config || Bbor.DEFAULT_CONFIG;
            this.fields = new FlexDict(this.config.staticFieldsRev);
            this.ctx = null;
            this.primitive = null;
            this.value = null;
            this.readingValue = false;
            this.end = false;
        }

        read(base62Decoder, compressor)
        {
            var token = this.readNext(base62Decoder, compressor);
            if (token === null) {
                return Bbor.Decoder.END;
            }

            switch (token) {
                case Bbor.Token.START_OBJECT:
                    // let's use a plain object instead of new Map() because string-based keys
                    // retain insertion order as of ES2015, so this should be equal to LinkedHashMap
                    var obj = {};
                    var next;
                    while ((next = this.read(base62Decoder, compressor)) !== Bbor.Decoder.END) {
                        obj[next] = this.read(base62Decoder, compressor);
                    }
                    return obj;

                case Bbor.Token.START_ARRAY:
                    var arr = [];
                    var el;
                    while ((el = this.read(base62Decoder, compressor)) !== Bbor.Decoder.END) {
                        arr.push(el);
                    }
                    return arr;

                case Bbor.Token.END_OBJECT:
                case Bbor.Token.END_ARRAY:
                    return Bbor.Decoder.END;

                default:
                    return this.value;
            }
        }

        readNext(inputStream, compressor)
        {
            var t, initialByte, majorType, additionalInfo, length;
            var retVal, bytes, positive, high, low;
            var i;

            if (!this.readingValue) {
                if (this.ctx !== null) {
                    t = this.ctx.token;
                    if (t === Bbor.Token.START_OBJECT || t === Bbor.Token.START_ARRAY) {
                        this.ctx.pos++;
                        if (this.ctx.pos === this.ctx.size) {
                            this.ctx = this.ctx.prev;
                            this.end = this.ctx === null;
                            return Bbor.TokenRev[t];
                        }
                    }
                }
                else {
                    if (this.end) {
                        return null;
                    }
                }
            }

            initialByte = this.readUint8(inputStream);
            majorType = initialByte >>> 5;
            additionalInfo = initialByte & 0x1f;
            length = this.readLength(inputStream, additionalInfo);

            this.value = null;
            this.primitive = null;

            switch (majorType) {
                case 0:
                    // in case of a 64bit number, length will hold the high/low parts and we need to rebuild it
                    if (additionalInfo === 27) {
                        this.value = (length.high * Bbor.POW_2_32) + length.low;
                    }
                    else {
                        this.value = length;
                    }
                    this.primitive = Bbor.Primitive.POSITIVE_INTEGER;
                    retVal = Bbor.Token.VALUE_NUMBER_INT;
                    break;

                case 1:
                    this.value = -1 - length;
                    this.primitive = Bbor.Primitive.NEGATIVE_INTEGER;
                    retVal = Bbor.Token.VALUE_NUMBER_INT;
                    break;

                case 2:
                    bytes = new Uint8Array(length);
                    for (i = 0; i < bytes.length; i++) {
                        bytes[i] = inputStream.read(8);
                    }
                    this.value = bytes;
                    this.primitive = Bbor.Primitive.BYTE_STRING;
                    retVal = Bbor.Token.START_ARRAY;
                    break;

                case 3:
                    this.value = this.readString(inputStream, compressor, length);
                    this.primitive = Bbor.Primitive.TEXT_STRING;
                    retVal = Bbor.Token.VALUE_STRING;
                    break;

                case 4:
                    this.ctx = new Bbor.Ctx(this.ctx, Bbor.Token.START_ARRAY, length);
                    retVal = Bbor.Token.START_ARRAY;
                    break;

                case 5:
                    this.ctx = new Bbor.Ctx(this.ctx, Bbor.Token.START_OBJECT, length * 2);
                    retVal = Bbor.Token.START_OBJECT;
                    break;

                case 6:
                    switch (additionalInfo) {
                        case 2:
                        case 3:
                            positive = additionalInfo === 2;
                            this.readingValue = true;
                            if (this.readNext(inputStream, compressor) === Bbor.Token.START_ARRAY) {
                                this.value = positive ? this.fromPositiveBigNum(this.value) :
                                    -1 - this.fromPositiveBigNum(this.value);
                                this.primitive = positive ? Bbor.Primitive.POSITIVE_BIGNUM : Bbor.Primitive.NEGATIVE_BIGNUM;
                                this.readingValue = false;
                                retVal = Bbor.Token.VALUE_NUMBER_INT;
                            }
                            else {
                                throw new Error("Encountered bignum, but the next token isn't a byte array");
                            }
                            break;
                        default:
                            throw new Error("Unimplemented tag value: " + additionalInfo);
                    }
                    break;

                case 7:
                    switch (additionalInfo) {
                        case 20:
                            this.value = false;
                            this.primitive = Bbor.Primitive.BOOLEAN;
                            retVal = Bbor.Token.VALUE_FALSE;
                            break;
                        case 21:
                            this.value = true;
                            this.primitive = Bbor.Primitive.BOOLEAN;
                            retVal = Bbor.Token.VALUE_TRUE;
                            break;
                        case 22:
                            this.value = null;
                            this.primitive = Bbor.Primitive.NULL;
                            retVal = Bbor.Token.VALUE_NULL;
                            break;
                        case 23:
                            this.value = undefined;
                            this.primitive = Bbor.Primitive.UNDEFINED;
                            retVal = Bbor.Token.VALUE_NULL;
                            break;
                        case 24:
                            this.value = this.readUint8(inputStream);
                            this.primitive = Bbor.Primitive.BYTE;
                            retVal = Bbor.Token.VALUE_NUMBER_INT;
                            break;
                        case 25:
                            throw new Error("Half-precision float not implemented");
                        case 26:
                            this.value = this.intBitsToFloat(length);
                            this.primitive = Bbor.Primitive.FLOAT32;
                            retVal = Bbor.Token.VALUE_NUMBER_FLOAT;
                            break;
                        case 27:
                            // note that length will hold the high/low bits here
                            this.value = this.longBitsToDouble(length);
                            this.primitive = Bbor.Primitive.FLOAT64;
                            retVal = Bbor.Token.VALUE_NUMBER_FLOAT;
                            break;
                        default:
                            throw new Error("Unimplemented floating-point or simple value: " + additionalInfo);
                    }
                    break;

                default:
                    throw new Error("Unknown major type: " + majorType);
            }

            if (this.ctx !== null && this.ctx.token === Bbor.Token.START_OBJECT && this.ctx.pos % 2 === 0) {
                this.primitive = null;
                this.value = this.parseField(this.value);
                this.ctx.field = this.value;
                retVal = Bbor.Token.FIELD_NAME;
            }

            return retVal;
        }

        readLength(inputStream, additionalInfo)
        {
            if (additionalInfo < 24) {
                return additionalInfo;
            }
            switch (additionalInfo) {
                case 24:
                    return this.readUint8(inputStream);
                case 25:
                    return this.readUint16(inputStream);
                case 26:
                    return this.readUint32(inputStream);
                case 27:
                    return this.readUint64(inputStream);
                default:
                    throw new Error("Invalid additional info: " + additionalInfo);
            }
        }

        readString(inputStream, compressor, length)
        {
            var bytes, i;
            if (this.config.enableStringCompression && compressor && length > 0) {
                return compressor.decompress(new WrappedByteReader(inputStream, length));
            }
            else {
                bytes = new Uint8Array(length);
                for (i = 0; i < length; i++) {
                    bytes[i] = inputStream.read(8);
                }
                return new TextDecoder().decode(bytes);
            }
        }

        readUint8(inputStream)
        {
            return inputStream.read(8) & 0xFF;
        }

        readUint16(inputStream)
        {
            return inputStream.read(16) & 0xFFFF;
        }

        readUint32(inputStream)
        {
            var high = this.readUint16(inputStream);
            var low = this.readUint16(inputStream);
            return (high * Bbor.POW_2_16) + low;
        }

        readUint64(inputStream)
        {
            // see encoder: since there's no support for 64-bit integers, let's return a split
            // Also note we don't need to reconstruct the parts using POW_2_32 because we'll write them to the raw bitstream
            // The >>> 0 is crucial here - it forces JavaScript to interpret the number as an unsigned 32-bit integer.
            // Without it, the negative value would be added instead of the intended unsigned value.
            return {
                high: inputStream.read(32) >>> 0,
                low: inputStream.read(32) >>> 0,
            };
        }

        fromPositiveBigNum(bytes)
        {
            var result = 0;
            for (var i = 0; i < bytes.length; i++) {
                result = (result << 8) | (bytes[i] & 0xFF);
            }
            return result;
        }

        parseField(nameOrIdx)
        {
            if (this.config.enableKeyMapping) {
                if (typeof nameOrIdx === 'string') {
                    this.fields.add(this.fields.size(), nameOrIdx);
                }
                else if (typeof nameOrIdx === 'number') {
                    nameOrIdx = this.fields.get(nameOrIdx);
                }
                else {
                    throw new Error("Invalid key type: " + typeof nameOrIdx);
                }
            }
            return nameOrIdx;
        }

        intBitsToFloat(bits)
        {
            // Create an ArrayBuffer with 4 bytes (32 bits)
            var buffer = new ArrayBuffer(4);
            // Create a DataView for binary manipulation
            var view = new DataView(buffer);
            // Store the 32-bit integer in the buffer
            view.setInt32(0, bits, false); // false = big-endian, as used in IEEE 754
            // Retrieve the floating-point number from the buffer
            var value = view.getFloat32(0, false); // false = big-endian

            // The reason for 7 digits is that IEEE 754 single-precision floats can represent approximately 6-7 decimal digits of precision.
            // Java automatically handles this for you because it maintains the 32-bit float type,
            // while in JavaScript we need to handle the display precision explicitly since it converts everything to 64-bit doubles.
            // the Number() is needed because toPrecision() returns a string (and it removed trailing zeros)
            // Note that we only apply this for decimal values or we'll lose int precision since this is also called
            // for (large) integers like Number.MAX_SAFE_INTEGER + 1
            if (Math.floor(value) !== value) {
                value = Number(value.toPrecision(7));
            }

            return value;
        }

        longBitsToDouble(bits)
        {
            // Create an ArrayBuffer with 8 bytes (64 bits)
            var buffer = new ArrayBuffer(8);
            // Create a DataView for binary manipulation
            var view = new DataView(buffer);
            // Store the high 32 bits and low 32 bits in the buffer
            view.setUint32(0, bits.high, false); // Top 32 bits (big-endian)
            view.setUint32(4, bits.low, false); // Bottom 32 bits (big-endian)
            // Retrieve the floating-point number from the buffer
            return view.getFloat64(0, false); // false = big-endian
        }
    }

    Bbor.Decoder = BborDecoder;
    // Empty objects compared with === or Object.is() return false unless they reference the exact same object.
    Bbor.Decoder.END = {};

    Bbor.DEFAULT_CONFIG = {

        INITIAL_FIELDS: [
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
        ],

        enableKeyMapping: true,
        enableStringCompression: true,
        staticFields: new Map(),
        staticFieldsRev: new Map(),

        init()
        {
            for (var i = 0; i < this.INITIAL_FIELDS.length; i++) {
                var c = this.INITIAL_FIELDS[i];
                if (!this.staticFields.has(c)) {
                    this.staticFields.set(c, i);
                    this.staticFieldsRev.set(i, c);
                }
                else {
                    throw new Error("Duplicate static field detected '" + c + "'");
                }
            }
        },
    };
    Bbor.DEFAULT_CONFIG.init();

    // ----- MAIN -----
    this.encode = function (obj)
    {
        var retVal = "";
        var base62Encoder = new BaseXStream.Encoder(function (str)
        {
            retVal += str;
        });
        new Bbor.Encoder().write(base62Encoder, new LZW(), obj);
        base62Encoder.flush();
        return retVal;
    };

    this.decode = function (str)
    {
        return new Bbor.Decoder().read(new BaseXStream.Decoder(str), new LZW());
    };

}]);