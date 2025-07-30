# bbor62 - A compact binary-to-text compressor

Since long, I've wanted to invent a general-purpose binary encoder with the following properties:

- bidirectional client/server: encoder and decoder in both Java and Javascript
- schemaless: no protobuf or avro, just plug-and-play
- streaming: no analysis, single pass only
- pure alphanumeric: [0-9][a-z][A-Z] and nothing else, so resulting strings are 'pretty' and double-click selection works as expected
- balanced implementation complexity, size and compression ratio
- no padding/trailing characters
- support for UTF-16
- no dependencies!

This project started out as a study of Pieroxy's lz-string compressor (see https://github.com/pieroxy/lz-string).
LZ-string is good, but I needed something stronger, especially for JSON payloads.
I ended up implementing a combination of CBOR + LZW + base62.

BBOR62 is efficient, portable and URL-safe.

For more details, see https://www.goudvuur.be/resource/Project/bbor62

Below, you find a few compression ratio comparisons for this JSON sample:

(see class be.goudvuur.base.bbor62.test.ComparisonTest)
````
{
  "organization": {
    "name": "Tech Innovators Inc.",
    "founded": 2010,
    "headquarters": {
      "street": "123 Innovation Way",
      "city": "San Francisco",
      "state": "CA",
      "zipCode": "94105"
    },
    "employees": [
      {
        "id": "E001",
        "firstName": "John",
        "lastName": "Smith",
        "role": "Software Engineer",
        "department": "Engineering",
        "skills": ["JavaScript", "Python", "AWS"],
        "contactInfo": {
          "email": "john.smith@techinnovators.com",
          "phone": "+1-555-123-4567",
          "extension": 101
        },
        "projects": [
          {
            "name": "Cloud Migration",
            "status": "in-progress",
            "deadline": "2024-12-31"
          },
          {
            "name": "Mobile App Development",
            "status": "completed",
            "deadline": "2024-06-30"
          }
        ]
      },
      {
        "id": "E002",
        "firstName": "Jane",
        "lastName": "Doe",
        "role": "Product Manager",
        "department": "Product",
        "skills": ["Agile", "Strategy", "User Research"],
        "contactInfo": {
          "email": "jane.doe@techinnovators.com",
          "phone": "+1-555-123-4568",
          "extension": 102
        },
        "projects": [
          {
            "name": "Market Analysis",
            "status": "pending",
            "deadline": "2024-09-30"
          }
        ]
      }
    ],
    "activeSites": true,
    "revenue": {
      "2022": 5000000,
      "2023": 7500000,
      "projected2024": 9223372036854775807
    }
  }
}
````
___
**base64: 133.33334%**  
Standard base64 compression on the UTF-8 based byte array.
This is basically the reason why I invented bbor62 in the first place: it blows up the source data size by 33%, despite the redundant JSON formatting syntax chars.
___
**base62: 134.38663%**  
Alphanumeric-only alternative to base64 (one of the goals).
Compression is of course slightly worse than base64 because of the more limited charset.
___
**lz-string uri: 67.90582%**  
A URI-compatible variant of lz-string that is based on 65 URI-safe characters.
Alas, the resulting string isn't very pretty. Compression is very good, though.
___
**cbor base64: 66.66667%**  
Cbor encodes to binary so the bytes need to be textified.
Using the full 64 charset of base64, compression is even better than lz-string.
But the resulting string is padded and uses the full 64 character alphabet.
___
**cbor base62: 67.038414%**  
When compressing to alphanumeric base62 instead of base64, compression ratio drops to the same level as lz-string. 
___
**bbor62: 66.35688%**  
Bbor62 combines all good features of cbor, lz-string and smart base62 encoding using bit packing when possible.
So its compression ratio reflects this. Strings are compressed using [LZW](https://en.wikipedia.org/wiki/Lempel%E2%80%93Ziv%E2%80%93Welch),
just like lz-string, which offers a much simpler codebase (eg. compared to LZ77 or LZMA) so the size of the JavaScript implementation is acceptable.
___