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

This project started out as a study of [Pieroxy's lz-string compressor](https://github.com/pieroxy/lz-string).
LZ-string is quite good, but doesn't meet my goals. I needed something better, especially for JSON payloads.
I ended up implementing my own encoder as a combination of CBOR + LZW + base62.

BBOR62 is efficient, portable and URL-safe.

A noob-friendly introduction to the algorithm is found at Goudvuur (in Dutch) https://www.goudvuur.be/resource/Project/bbor62

---
## Comparison with other encoders/compressors
Below, you find a few compression comparisons ratios (and extra details) when encoding this JSON sample:  
*(see class be.goudvuur.base.bbor62.test.ComparisonTest)*

### Comparison overview
*(continue reading below the JSON sample for more details)*

| Encoder           | Compressed chars | Compression ratio |
|-------------------|------------------|-------------------|
| original          | 1614             | 100.00%           |
| base64            | 2152             | 133.33%           |
| base62            | 2169             | 134.38%           |
| lz-string utf-16  | 411              | 25.46%            |
| lz-string uri     | 1096             | 67.90%            |
| cbor base64       | 1076             | 66.66%            |
| cbor base62       | 1082             | 67.03%            |
| bbor62 string     | 1071             | 66.35%            |
| **bbor62 object** | **769**          | **47.64%**        |


### Sample JSON to encode
```
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
```

### Encoded using `base64`

Standard base64 compression on the UTF-8 based byte array.
This is basically the reason why I invented bbor62 in the first place: it blows up the source data size by 33%, despite the redundant JSON formatting syntax chars.

**Compression ratio: 133.33%**  
**Results in 2152 chars**:  
`ewogICJvcmdhbml6YXRpb24iOiB7CiAgICAibmFtZSI6ICJUZWNoIElubm92YXRvcnMgSW5jLiIsCiAgICAiZm91bmRlZCI6IDIwMTAsCiAgICAiaGVhZHF1YXJ0ZXJzIjogewogICAgICAic3RyZWV0IjogIjEyMyBJbm5vdmF0aW9uIFdheSIsCiAgICAgICJjaXR5IjogIlNhbiBGcmFuY2lzY28iLAogICAgICAic3RhdGUiOiAiQ0EiLAogICAgICAiemlwQ29kZSI6ICI5NDEwNSIKICAgIH0sCiAgICAiZW1wbG95ZWVzIjogWwogICAgICB7CiAgICAgICAgImlkIjogIkUwMDEiLAogICAgICAgICJmaXJzdE5hbWUiOiAiSm9obiIsCiAgICAgICAgImxhc3ROYW1lIjogIlNtaXRoIiwKICAgICAgICAicm9sZSI6ICJTb2Z0d2FyZSBFbmdpbmVlciIsCiAgICAgICAgImRlcGFydG1lbnQiOiAiRW5naW5lZXJpbmciLAogICAgICAgICJza2lsbHMiOiBbIkphdmFTY3JpcHQiLCAiUHl0aG9uIiwgIkFXUyJdLAogICAgICAgICJjb250YWN0SW5mbyI6IHsKICAgICAgICAgICJlbWFpbCI6ICJqb2huLnNtaXRoQHRlY2hpbm5vdmF0b3JzLmNvbSIsCiAgICAgICAgICAicGhvbmUiOiAiKzEtNTU1LTEyMy00NTY3IiwKICAgICAgICAgICJleHRlbnNpb24iOiAxMDEKICAgICAgICB9LAogICAgICAgICJwcm9qZWN0cyI6IFsKICAgICAgICAgIHsKICAgICAgICAgICAgIm5hbWUiOiAiQ2xvdWQgTWlncmF0aW9uIiwKICAgICAgICAgICAgInN0YXR1cyI6ICJpbi1wcm9ncmVzcyIsCiAgICAgICAgICAgICJkZWFkbGluZSI6ICIyMDI0LTEyLTMxIgogICAgICAgICAgfSwKICAgICAgICAgIHsKICAgICAgICAgICAgIm5hbWUiOiAiTW9iaWxlIEFwcCBEZXZlbG9wbWVudCIsCiAgICAgICAgICAgICJzdGF0dXMiOiAiY29tcGxldGVkIiwKICAgICAgICAgICAgImRlYWRsaW5lIjogIjIwMjQtMDYtMzAiCiAgICAgICAgICB9CiAgICAgICAgXQogICAgICB9LAogICAgICB7CiAgICAgICAgImlkIjogIkUwMDIiLAogICAgICAgICJmaXJzdE5hbWUiOiAiSmFuZSIsCiAgICAgICAgImxhc3ROYW1lIjogIkRvZSIsCiAgICAgICAgInJvbGUiOiAiUHJvZHVjdCBNYW5hZ2VyIiwKICAgICAgICAiZGVwYXJ0bWVudCI6ICJQcm9kdWN0IiwKICAgICAgICAic2tpbGxzIjogWyJBZ2lsZSIsICJTdHJhdGVneSIsICJVc2VyIFJlc2VhcmNoIl0sCiAgICAgICAgImNvbnRhY3RJbmZvIjogewogICAgICAgICAgImVtYWlsIjogImphbmUuZG9lQHRlY2hpbm5vdmF0b3JzLmNvbSIsCiAgICAgICAgICAicGhvbmUiOiAiKzEtNTU1LTEyMy00NTY4IiwKICAgICAgICAgICJleHRlbnNpb24iOiAxMDIKICAgICAgICB9LAogICAgICAgICJwcm9qZWN0cyI6IFsKICAgICAgICAgIHsKICAgICAgICAgICAgIm5hbWUiOiAiTWFya2V0IEFuYWx5c2lzIiwKICAgICAgICAgICAgInN0YXR1cyI6ICJwZW5kaW5nIiwKICAgICAgICAgICAgImRlYWRsaW5lIjogIjIwMjQtMDktMzAiCiAgICAgICAgICB9CiAgICAgICAgXQogICAgICB9CiAgICBdLAogICAgImFjdGl2ZVNpdGVzIjogdHJ1ZSwKICAgICJyZXZlbnVlIjogewogICAgICAiMjAyMiI6IDUwMDAwMDAsCiAgICAgICIyMDIzIjogNzUwMDAwMCwKICAgICAgInByb2plY3RlZDIwMjQiOiA5MjIzMzcyMDM2ODU0Nzc1ODA3CiAgICB9CiAgfQp9`

### Encoded using `base62`

Alphanumeric-only alternative to base64 (one of the goals).
Compression is of course slightly worse than base64 because of the limited alphabet.

**Compression ratio: 134.38%**  
**Results in 2169 chars:**  
`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`

### Encoded using `lz-string utf-16`

Classic lz-string encoding transforms the source data to an UTF-16 encoded string, so it can be stored in localStorage.
By creating a 16bit token space when processing mainly ascii characters (using a modified LZW compressor),
very good compression results are achieved up to 1/4 of the original length using only string compression.
However, the resulting data string is ugly and unusable for our goals.

**Compression ratio: 25.46%**  
**Results in 411 chars:**  
`㞂耄恄འ与恃ǘዀ庈஫곈׆⠐䤲蠋怩ꅐʭ䃆Y耤닋䀛뙰ٰ耺䠀槂钀೚W搀䶫⮠ঀ̀䘭卉䅭儲肎଒싍嘀먥ീᖖ㕪壪䇑ꀳヮ㸜㰰u䐀佉楃䠦听⢢䠀攔　ㅘᑸ脦桨ꝧⱬ婤聡A䊧䡴吀ݲ⼀一ᘽV䠘뀀弃ᥪ䨆耛栈∀浾衇⋈呵撀儭崚闙吻Ⰰ㤪㊨)栖籡闱쐗᎚⼔쨄陝?桱돔ᘀ໥暡耶졸⨙໥艽抪ڕ謃䙂礶部氺ἇ蓈Ѐ歔㣜欳Ǎ⃧䐟Ԥ얂㔼ረ䈥腡偊ꡅ⤀˪?老戉薄᲋媨턌䫰¬껈儀跮저ࣙ壐삿໊⟊僱넆蜦ᗠ͐득ݭꟋ疺6;⦤怀 롼ᄏ䎡鑀蘑ꠃ寨깡惉ଢꆙ抛⁑鸕䦂饆쪡悍?ᅳ怔擕눖?茗ꠂ笐봹Ւ駆墯㚃䇒瑩涾ᴟ崹᭭㣓?㴀ě዁锆荌ᚠ錆ꨱ鹟쒔냕꼱ꉡ﨑♥㝲?듮豾㠛䟹㊨텚熐혯ୢ퀴὎䀹ṓ诂ꔭ䧌螹ꂬ쨀?威ઁ빟识斾攁䜔ଔᄇ腬࠱ᙄཛྷ㼌⌏盐阥䤺ʔꢡᰩ镉屒鸂褸耕䁅냀	妱ㅠ嘀哂ҧᔥʈ萃戹䅖䤕ᐚ蔑鑴㭖恘㵗耴У威㔰눜폀︛廔疝坃퀀㡯⠚蟵ꡀ?ꃴച㟼午Ʒ跅Ⓢ橙쯇첬Ƀ쌁⨊ᱠ袃囈⭓우梼፲졎?〥??﷓忈ꖫ䡁徤胅ᰇ煈ቦ캅瀔樈咀??ᖏ뜂衮魡鯴ᥒϭ簺Ꮦ魦짘⮌ጵ᧳ꃚഏ엱㵭ះ疜ꓴ囧⭄䚻耀`

### Encoded using `lz-string uri`

A URI-compatible variant of classic lz-string that doesn't use UTF-16, but is based on a 65 char, URI-safe alphabet.
Alas, the resulting string isn't very pretty (double-click the first char), and only string compression is used. Results are acceptable, though.
Note that the main reason why alphanumeric encoding (base62) is preferred is obvious here:
try to double-click (or long-tap) the first character of the encoded string to select the entire value, and you'll see (depending on your OS)
it only selects part of the text. This is cumbersome in situations where you need to frequently copy/paste values or URL/URIs with embedded values (esp. on mobile);
you can't just double-click the URL, but you have to scroll to the very end to do a select-all.

**Compression ratio: 67.90%**  
**Results in 1096 chars:**  
`N4KABGBED2BODmBDAdgSwF6IC6usyAXGKBBJMogLYCmhUAKtQMYAWYAksstAG7ZwBnDsiYA6SABpwpSADNoAV2QATasroAmAAwBGLVNJQW1RMoCOCxLCzVYAuiUNQBWWNWpY6kHRoDMw7j4cPDAAdUQAT0lpQ0gmVCwookgAZRQwADFYFHiBJmhop2csbFpkgGEAQUKnSHRUAAdy6FUvAE4AFj0AVkgYsABfAxlqSgaAG2gI93siAG1+iEciyFR1ZIBRLV0aldlUOywAOSoyqAApaBZ8YZXxxBcTmi8UygSWXdrYaHGz1OhZFgAO5WahgDbIeCoZDuWCfWKqBpWLA0ZCeTaQ6Gw6HweEyAQAa1Q43GszAc0g50QfBSTFgjU8EigAAUIlgWHhJFBKqEUpAALq3Wr5NGIJhYTjyByLWqjRDErwAKyuyFEAje7IAAjZWNDAvw7KJ8pQ8bEGhyYV4ANQ6AC03Qdtp8vltHW6ADYAOymkYADxsyAEuHwRD0OhlECGEagDW+iuYWDJCyKhmWKbIFGeFUmCmUYAAsqh4Nlgjdo7EXNgFGTVshbbHoMXqAJ7EL05BVKZxlivNoNB0nRpbb4dH105G2040+3M3989AAEbEsGVBoNMAAEWoPGokwaqMZ5fxJSw1a8xomHjUPoRJmU3ctyT7A607uHWjH44G0f5MqjRWnGQ1i8LYtA0H05AOR5Ti8KlLUnMh7mgrMoA3aBaAQqBvl+LxmW+ZQFHFAsUEQeBbAgxFkQPXD8MIw920JYlSToClKihHCmVSVxSngKJOIAVQEWwwAAJWbExYFYAVMLiPASnFSUCiIQDYjlBVkkVFBqFEZR0O1ZgWD1XgDQEI1oBNTCyHNPA-hte1HWdV0PQADhvKBqH9ahA2DOg9A0aN-xTSAG3jcUkyPFTalnLx8ysAkPDASoKHGCIg1bI8yErU8awaLzlBxNyyE7e8eyfMCXzad9P3Tb901-IpatIQV+kgMUcB3FIEmbOhXAUaghUgNwd2QPrpRWPtwKIbpthm-QZUgPtfDoT1ptmydgrjBM1GfOg2g0PxfE9bRfHdZzug6T0VucrRPX6RrvwGIA$`

### Encoded using `cbor base64`

Cbor encodes to binary so the bytes need to be textified.
Using the full 64 charset of base64, compression is already better than lz-string.
But the resulting string is padded and uses the full 64 character alphabet.

**Compression ratio: 66.66%**  
**Results in 1076 chars:**
`oWxvcmdhbml6YXRpb26mZG5hbWV0VGVjaCBJbm5vdmF0b3JzIEluYy5nZm91bmRlZBkH2mdyZXZlbnVlo2QyMDIyGgBMS0BkMjAyMxoAcnDgbXByb2plY3RlZDIwMjT6XwAAAGllbXBsb3llZXOCqGJpZGRFMDAxZHJvbGVxU29mdHdhcmUgRW5naW5lZXJmc2tpbGxzg2pKYXZhU2NyaXB0ZlB5dGhvbmNBV1NobGFzdE5hbWVlU21pdGhocHJvamVjdHOCo2RuYW1lb0Nsb3VkIE1pZ3JhdGlvbmZzdGF0dXNraW4tcHJvZ3Jlc3NoZGVhZGxpbmVqMjAyNC0xMi0zMaNkbmFtZXZNb2JpbGUgQXBwIERldmVsb3BtZW50ZnN0YXR1c2ljb21wbGV0ZWRoZGVhZGxpbmVqMjAyNC0wNi0zMGlmaXJzdE5hbWVkSm9obmpkZXBhcnRtZW50a0VuZ2luZWVyaW5na2NvbnRhY3RJbmZvo2VlbWFpbHgdam9obi5zbWl0aEB0ZWNoaW5ub3ZhdG9ycy5jb21lcGhvbmVvKzEtNTU1LTEyMy00NTY3aWV4dGVuc2lvbhhlqGJpZGRFMDAyZHJvbGVvUHJvZHVjdCBNYW5hZ2VyZnNraWxsc4NlQWdpbGVoU3RyYXRlZ3ltVXNlciBSZXNlYXJjaGhsYXN0TmFtZWNEb2VocHJvamVjdHOBo2RuYW1lb01hcmtldCBBbmFseXNpc2ZzdGF0dXNncGVuZGluZ2hkZWFkbGluZWoyMDI0LTA5LTMwaWZpcnN0TmFtZWRKYW5lamRlcGFydG1lbnRnUHJvZHVjdGtjb250YWN0SW5mb6NlZW1haWx4G2phbmUuZG9lQHRlY2hpbm5vdmF0b3JzLmNvbWVwaG9uZW8rMS01NTUtMTIzLTQ1NjhpZXh0ZW5zaW9uGGZrYWN0aXZlU2l0ZXP1bGhlYWRxdWFydGVyc6RkY2l0eW1TYW4gRnJhbmNpc2NvZXN0YXRlYkNBZnN0cmVldHIxMjMgSW5ub3ZhdGlvbiBXYXlnemlwQ29kZWU5NDEwNQ==`

### Encoded using `cbor base62`

When compressing to alphanumeric base62 instead of base64, compression ratio drops a bit, to around the same level as lz-string.

**Compression ratio: 67.03%**  
**Results in 1082 chars:**
`7CYNHRFrthg2YHPdstF9Q0E7xmE3cdu5sdFYVy6UTBQRBgy9WUlL1Vq9CgRuTE9YicsquxmBvinkuQrbe56m8EuTAp3caLmx23moMhEhcaldDDmZe2fWgz0H6zn4dKVzWhbdjQucevjyIC9CS9fpxcdykDA8VDdbk9rJlJJaKVmz08WeOK81HkyKFy9NN8OMKbIgw9xGRcqBqxIN9DP20eMe5UO1foJMZJF7WjihAolIpvjFyeq7GNeet6B654Ck7NP12KVQGzh2wJM2FovhAuvpRnhCTiYOwXraFMWxNS4oGeDNUNbPYslmjR9ihL9iVXy5ZPlQppewOVVsZ57rgpkTcn0T3bspeMERFBfgtZpCo7Wi52pIzN5NJ3mijPY9wWjqKoXYwEmMKW1ERsRptUsjcJzhquc7wxugipCycOULoiX6FqnidfAO2leIQhXQjJ9y8I52unz1h2nXGfVyLSs8d96kfbB5UKahw4g5Zdl1C9Cmk6G9esPVFP8IeKtYCe6LOygojRu1xG342Ff9W0BuRrkiegfmf8DN7fpVzZI60FO1ZZX2XgZB9D0Xcy3MV7vpx9covnLyIb7GdISJNWtAfyoaP6jPtWsgM7wmYQmUo99dIjylBExdIXJufa04ZUjVOjKVRSeZCPUxbC6V1ThsgRQqSA9oxqoWv1AhHHo7Nf4EZN78Kw4gFZSMbFR050PuNQC8v4Mpm03p7IR2Ca1qGd5h73yif2e42Iss8PzUV4crpNAv1aYQcsYgfYmKui7DuO1QiP01ilGhD13i8rHhHlY5KSO2LJPNukweckF2ykdGwcPCrJqJvFXRjcibLZt4Uiv42dKNe6gqhvpNYhpMK7RXS8h7EZLKsqP6JvCdsjjqakyHVIwgQqFzeXeu2rzj8tL31w15ETeNoHHWhZqzgkb9oMfQvAjIGs2pgdqRt66cdKEWbNWo0tnJjDoMs7MqAvMJbffKAQ7AP3a3BeVONlMa7CVvFAy5F5fzCnsPMMHxoSZ67RxP4Wxyfz43NIbclQLpBmKut90zYutHriJqZJ7KaQPtVHz3AJIwRE1PuTIeRK5S2vkV1R`

### Encoded using `bbor62 string`

Bbor62 combines all good features of cbor, lz-string and smart base62 encoding and uses bit squeezing when possible.
So even when compressing the JSON as a plain string, compression ratio is already acceptable.
Strings are compressed using [LZW](https://en.wikipedia.org/wiki/Lempel%E2%80%93Ziv%E2%80%93Welch), just like lz-string (kind of),
which offers a much simpler codebase (eg. compared to LZ77 or LZMA) so the size of the JavaScript implementation is acceptable.

**Compression ratio: 66.35%**  
**Results in 1071 chars** 
`GdYjZKS02m6C09612ixweiJUAD720Ux7wPLf5oBcHJtG9JvyccSakWk2WFkH5pEkg31Ic3PODd4biFuwmSsS6cVH4kei8HbwACTg2LaccHNBt2WLjNZS7NRIr4WshCeF2TXb0HDDVtyMg0G0jWrSgAUiDR135LGW6cU7htR6CZHJfGj5v6Ps1FAh0fcSiNJxiFRGZbpvmveDVHQPFYwSGnxU0CEgMRXEBQ96yw1x9gBot056BNcB9El03zMnvNcbiZSK8odh5aiCCeUif9Ua5GTbrS0ui9E8244EDNZAckUysBi4FW0e2mEkkp5gc1Yr4obqFpee4g8AzzmraoB13zLLDNoW2ORwZRLcna0x6VO96m7k5vvs1bZLSG990YCGiUGEhIgOCjbwW4fK90REELXl4bZQ1KA8JL7lzYsY5Yms8s3Fvonkje830Cmri85u1pPmznn6FQsFGf8jV7sm5mlTDL4c79W4enO5n6K664HTZuCWDiES9vmUa4B2vvGQ7yWOvFOG2Twe5tEAmIyKfSKwC2xvaFG8XwccFsIPev9N8tqKjRhJ07Cq2cAEZQtvsjCv8kjKOz32kEkphFSP4dC83K7PrvmR8rErjCv0jIff0P0FkQ8mYGnO3hKLBotcPckK1A6i2hv51w5ckvmOW71TbPyGjRCK4PMadfpi3gnTnmFdarfMF3MTQTmcGMcU67jvmafO0tScopWD4T3G2OA6BLzT2YRK3hfxXKjRrTFm44sz4qgkGXnZ3mQg24bl8HX0Qh4u1eRN5EDGXjE3c1jFespvsUvj1BDBcsdKrPpE23ySwNLLcyk6VNWp8qXj0KdWCFWFyDKcmRXv1kpEjhJiHSmJ4mXxBCWq9BRC27OcLNqSfBVlk2ZK2eI42prMfo0A6cmCcjoOApembLDlHDjHHMpNmgjCAbRIRtbbXzYrquaW0YHwGWBhrBim3cyZiQYI7fPWo5yniMpUGEI54xiDWhVb2YjA5xw60WQSrabg1zUzeCxd2rqi1P1cgjAAGNlj9BaibPTf0NRxB02SeC4T0H4K1vRQglNC04HRqcgT0rB`

### Encoded using `bbor62 object`

This is where Bbor62 really shines. When passing the sample data as an object, all bbor62 features come to light.
Syntax is abstracted away, strings are compressed, common field names are compressed and bits are squeezed together as tightly as possible.
Compared with naive base62 encoding, bbor62 object compression is nearly 3x as effective!

**Compression ratio: 47.64%**  
**Results in 769 chars:**  
`iNsM9To155XR1H7cqsZwbHju6qOvBKTsVhdq5pEiSOJotMNHuN2114wX40DQESS10IHhAJfv5333VU9e1fN08D494x5C0YQOa1YYaYFxESBPFXwq4UfQAf5CMq5C64SB2kn0tpFag8vZZP4zxwud0vjODry0GWLg0Qgu16SkSdDm2NQ216f5xgZwsnLl9aib7L3W9AX65co09OESUIFP4PS5osY23Vixe3JUCLx5F9uEr9qoErZwltJoXR3wbTTAChFOSex6D5G01CvF3RUCVxAc6x8LCXjr65Dc5b42ivbY2JQn2WKz9FSQkIbw1zyGEH938rAyFc4U83DTFXGfZbpr8vYy0g9iqrhYFQsE0tQKAVin9BBQBZg5EATOV6kO5ViKBbGAvZmO6t5X0in2GeCtIJspEJiS6kmc8HpdcfwZFRqu0v0i0YELiAUy16rT0Ar4FxXGDT5BHLRmriynFQkD13Fxo8FU00u5XdEc1q1I3uAMjWfA2DXE09vE1hpUlIUI7WHs05AGCeslQQ3Q37K5iDNm7Ske0W6q0MnaGp8M1NGc1rcfDPlYEAjae86aGXXOsvbu5WaW4tWb2ZpgpztIrmACeA8kb1rT4lyMXi3yUzSO5Erc1kIJX1Ck1n6IZXfdiPp3ZfZK6cT2kRPO8FZCdmFM6LRM0NRTDtQ8ZeOe1elVkO7k2DwQ1mE5BL4XiLjDl4dWGmgQ93dGROfB00rg5FBI3QKe5V5M0yyF0RK32oIvGbuU9y1PZCG7ZCG77`

---
## Licensing

Please see the file called LICENSE.
