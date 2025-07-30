package be.goudvuur.base.bbor62.test;

import be.goudvuur.base.bbor62.Bbor62;
import be.goudvuur.base.bbor62.Logger;
import blazing.chain.LZSEncoding;
import com.google.common.io.BaseEncoding;
import com.upokecenter.cbor.CBORObject;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Created by bram on Jul 30, 2025
 */
public class ComparisonTest
{
    //-----CONSTANTS-----
    private static final String[] SAMPLES = {
                    "{\n" +
                    "  \"organization\": {\n" +
                    "    \"name\": \"Tech Innovators Inc.\",\n" +
                    "    \"founded\": 2010,\n" +
                    "    \"headquarters\": {\n" +
                    "      \"street\": \"123 Innovation Way\",\n" +
                    "      \"city\": \"San Francisco\",\n" +
                    "      \"state\": \"CA\",\n" +
                    "      \"zipCode\": \"94105\"\n" +
                    "    },\n" +
                    "    \"employees\": [\n" +
                    "      {\n" +
                    "        \"id\": \"E001\",\n" +
                    "        \"firstName\": \"John\",\n" +
                    "        \"lastName\": \"Smith\",\n" +
                    "        \"role\": \"Software Engineer\",\n" +
                    "        \"department\": \"Engineering\",\n" +
                    "        \"skills\": [\"JavaScript\", \"Python\", \"AWS\"],\n" +
                    "        \"contactInfo\": {\n" +
                    "          \"email\": \"john.smith@techinnovators.com\",\n" +
                    "          \"phone\": \"+1-555-123-4567\",\n" +
                    "          \"extension\": 101\n" +
                    "        },\n" +
                    "        \"projects\": [\n" +
                    "          {\n" +
                    "            \"name\": \"Cloud Migration\",\n" +
                    "            \"status\": \"in-progress\",\n" +
                    "            \"deadline\": \"2024-12-31\"\n" +
                    "          },\n" +
                    "          {\n" +
                    "            \"name\": \"Mobile App Development\",\n" +
                    "            \"status\": \"completed\",\n" +
                    "            \"deadline\": \"2024-06-30\"\n" +
                    "          }\n" +
                    "        ]\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"id\": \"E002\",\n" +
                    "        \"firstName\": \"Jane\",\n" +
                    "        \"lastName\": \"Doe\",\n" +
                    "        \"role\": \"Product Manager\",\n" +
                    "        \"department\": \"Product\",\n" +
                    "        \"skills\": [\"Agile\", \"Strategy\", \"User Research\"],\n" +
                    "        \"contactInfo\": {\n" +
                    "          \"email\": \"jane.doe@techinnovators.com\",\n" +
                    "          \"phone\": \"+1-555-123-4568\",\n" +
                    "          \"extension\": 102\n" +
                    "        },\n" +
                    "        \"projects\": [\n" +
                    "          {\n" +
                    "            \"name\": \"Market Analysis\",\n" +
                    "            \"status\": \"pending\",\n" +
                    "            \"deadline\": \"2024-09-30\"\n" +
                    "          }\n" +
                    "        ]\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"activeSites\": true,\n" +
                    "    \"revenue\": {\n" +
                    "      \"2022\": 5000000,\n" +
                    "      \"2023\": 7500000,\n" +
                    "      \"projected2024\": 9223372036854775807\n" +
                    "    }\n" +
                    "  }\n" +
                    "}"
    };

    private static final String SEP = "_______________________________________________________________________________";

    //-----VARIABLES-----

    //-----CONSTRUCTORS-----

    //-----PUBLIC METHODS-----
    public static void main(String[] args) throws IOException
    {
        for (String sample : SAMPLES) {

            String compressedBbor62 = Bbor62.encode(sample);

            String compressedLz = LZSEncoding.compress(sample);
            String compressedLz64 = LZSEncoding.compressToBase64(sample);
            String compressedLzUri = LZSEncoding.compressToEncodedURIComponent(sample);
            String compressedBase64 = BaseEncoding.base64().encode(sample.getBytes(StandardCharsets.UTF_8));
            String compressedBase62 = encodeToBase62(sample.getBytes(StandardCharsets.UTF_8));
            String compressedCbor64 = BaseEncoding.base64().encode(CBORObject.FromJSONString(sample).EncodeToBytes());
            String compressedCbor62 = encodeToBase62(CBORObject.FromJSONString(sample).EncodeToBytes());

            Logger.log("Compression ratios: ");
            Logger.log(SEP);

            Logger.log("\toriginal: " + sample);
            Logger.log("\toriginal: " + ((float) sample.length() / sample.length() * 100) + "%");

            Logger.log("\tbase64: " + compressedBase64);
            Logger.log(SEP);
            Logger.log("\tbase64: " + ((float) compressedBase64.length() / sample.length() * 100) + "%");
            Logger.log(SEP);

            Logger.log("\tbase62: " + compressedBase62);
            Logger.log(SEP);
            Logger.log("\tbase62: " + ((float) compressedBase62.length() / sample.length() * 100) + "%");
            Logger.log(SEP);

            Logger.log("\tlz-string utf-16: " + compressedLz);
            Logger.log(SEP);
            Logger.log("\tlz-string utf-16: " + ((float) compressedLz.length() / sample.length() * 100) + "%");
            Logger.log(SEP);

            Logger.log("\tlz-string base64: " + compressedLz64);
            Logger.log(SEP);
            Logger.log("\tlz-string base64: " + ((float) compressedLz64.length() / sample.length() * 100) + "%");
            Logger.log(SEP);

            Logger.log("\tlz-string uri: " + compressedLzUri);
            Logger.log(SEP);
            Logger.log("\tlz-string uri: " + ((float) compressedLzUri.length() / sample.length() * 100) + "%");
            Logger.log(SEP);

            Logger.log("\tcbor base64: " + compressedCbor64);
            Logger.log(SEP);
            Logger.log("\tcbor base64: " + ((float) compressedCbor64.length() / sample.length() * 100) + "%");
            Logger.log(SEP);

            Logger.log("\tcbor base62: " + compressedCbor62);
            Logger.log(SEP);
            Logger.log("\tcbor base62: " + ((float) compressedCbor62.length() / sample.length() * 100) + "%");
            Logger.log(SEP);

            Logger.log("\tbbor62: " + compressedBbor62);
            Logger.log(SEP);
            Logger.log("\tbbor62: " + ((float) compressedBbor62.length() / sample.length() * 100) + "%");
            Logger.log(SEP);
        }
    }

    //-----PROTECTED METHODS-----

    //-----PRIVATE METHODS-----
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static String encodeToBase62(byte[] bytes)
    {
        BigInteger value = new BigInteger(1, bytes); // 1 => positive

        StringBuilder sb = new StringBuilder();
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = value.divideAndRemainder(BigInteger.valueOf(62));
            sb.append(BASE62.charAt(divmod[1].intValue()));
            value = divmod[0];
        }

        return sb.reverse().toString();
    }
}
