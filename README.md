# bbor62 - A compact binary-to-text compressor

Since long, I've wanted to invent a general-purpose binary encoder with the following properties:

- bidirectional client/server: encoder and decoder in both Java and Javascript
- schemaless: no protobuf or avro, just plug-and-play
- streaming: no analysis, single pass only
- pure alphanumeric: [0-9][a-z][A-Z] and nothing else, so double-click selection works as expected, on all OS's
- balanced implementation complexity, size and compression ratio
- no padding, UTF-16 support, ...
- no dependencies!

This project started out as a study of Pieroxy's lz-string compressor (see https://github.com/pieroxy/lz-string).
LZ-string is good, but I needed something stronger, especially for JSON payloads.
I ended up implementing a combination of CBOR + LZW + base62.

BBOR62 is efficient, portable and URL-safe.

For more details, see https://www.goudvuur.be/resource/Project/bbor62
