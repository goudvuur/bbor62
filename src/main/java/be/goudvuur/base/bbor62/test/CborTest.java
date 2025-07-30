/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.test;

import be.goudvuur.base.bbor62.BaseXStream;
import be.goudvuur.base.bbor62.Bbor;
import be.goudvuur.base.bbor62.LZW;
import be.goudvuur.base.bbor62.Logger;
import be.goudvuur.base.bbor62.jackson.BborGenerator;
import be.goudvuur.base.bbor62.jackson.BborParser;
import com.fasterxml.jackson.core.ErrorReportConfiguration;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by bram on Nov 25, 2024
 */
public class CborTest
{
    // Example usage and unit tests
    public static void main(String[] args) throws Exception
    {
//        LZW lzw = new LZW(LZW.DEFAULT_CONFIG);
//        BaseXStream.Decoder stream = new BaseXStream.Decoder("ZbwUDN153Jja07kY8yI69s302d61aa93ClEuIjl51Ee", BaseXStream.DEFAULT_CONFIG);
//        Object result = new Bbor.Decoder(Bbor.DEFAULT_CONFIG).read(stream, lzw);
//        Logger.log(result);

        List<String> samples = new ArrayList<>();

//        samples.add("[{\"resourceTypeCurie\":\"_typeOfLabel\"},{\"resourceTypeCurie\":\"rdfs:label\"}]");
//
//        samples.add("{\n" +
//                    "  \"org\": {\n" +
//                    "    \"name\": \"A\"\n" +
//                    "  }\n" +
//                    "}");
//
//        // test with 2 byte UTF-8 character
//        samples.add("{\n" +
//                    "    \"name\": \"T ā Inc.\"\n" +
//                    "}");
//
//        // test with 3 byte UTF-8 character
//        samples.add("{\n" +
//                    "    \"name\": \"T € Inc.\"\n" +
//                    "}");
//
//        // another test with 3 byte UTF-8 character
//        samples.add("{\n" +
//                    "    \"name\": \"T 中 한 € ♠ ∑ Ж Inc.\"\n" +
//                    "}");
//
//        // test with 4 byte UTF-8 character
//        samples.add("{\n" +
//                    "    \"name\": \"T \uD83D\uDE00 Inc.\"\n" +
//                    "}");
//
//        // test with multiple 4 byte UTF-8 character
//        samples.add("{\n" +
//                    "    \"name\": \"T \uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08 Inc.\"\n" +
//                    "}");
//
//        samples.add("{\n" +
//                    "  \"org\": {\n" +
//                    "    \"name\": \"Tech ā Inc.\"\n" +
//                    "  }\n" +
//                    "}");
//
//        samples.add("{\n" +
//                    "  \"organization\": {\n" +
//                    "    \"name\": \"Tech Innovators ā Inc.\"\n" +
//                    "  }\n" +
//                    "}");
//
//        samples.add("{\n" +
//                    "      \"city\": \"San Francisco\"\n" +
//                    "}");
//
//        // little test with array
//        samples.add("{\n" +
//                    "    \"name\": \"Tech inc.\",\n" +
//                    "    \"revenue\": [1234, 5678, 8901]\n" +
//                    "}");
//
//        samples.add("{\n" +
//                    "    \"name\": \"Te\",\n" +
//                    "    \"fded\": 2010\n" +
//                    "}");
//
//        samples.add("{\n" +
//                    "  \"organization\": {\n" +
//                    "    \"name\": \"Tech ā Inc.\",\n" +
//                    "    \"city\": \"San Francisco\"\n" +
//                    "  }\n" +
//                    "}");
//
//        samples.add("{\n" +
//                    "  \"organization\": {\n" +
//                    "    \"name\": \"Tech Innovators ā Inc.\",\n" +
//                    "    \"founded\": 2010,\n" +
//                    "    \"headquarters\": {\n" +
//                    "      \"street\": \"123 Innovation Way\",\n" +
//                    "      \"city\": \"San Francisco\",\n" +
//                    "      \"state\": \"CA\",\n" +
//                    "      \"zipCode\": \"94105\"\n" +
//                    "    }\n" +
//                    "  }\n" +
//                    "}");

        samples.add("{\n" +
                    "    \"revenue\": {\n" +
                    "      \"2022\": 5000000,\n" +
                    "      \"2023\": 7500000,\n" +
                    "      \"projected2024\": 9223372036854775807\n" +
                    "    }\n" +
                    "}");

        // test for dict reset with limit 66
        samples.add("{\n" +
                    "  \"numbers\": {\n" +
                    // note that these 3 are in quotes because > MAX_SAFE_INTEGER
                    "    \"bignum_small\": \"18446744073709551616\"\n" +
                    "  }\n" +
                    "}");

        samples.add("{\n" +
                    "  \"numbers\": {\n" +
                    "    \"small_pos\": 23,\n" +
                    "    \"small_neg\": -24,\n" +
                    "    \"uint8_max\": 255,\n" +
                    "    \"uint16_max\": 65535,\n" +
                    // 2^32 - 1 (problematic because Java uses 31 bits for an int + 1 sign bit)
                    "    \"uint32_max\": 4294967295,\n" +
                    "    \"js_max_safe\": 9007199254740991,\n" +
                    // problematic because it tests the threshold
                    "    \"js_max_safe_plus_one\": 9007199254740992,\n" +
                    // note that these 3 are in quotes because > MAX_SAFE_INTEGER
                    "    \"bignum_small\": \"18446744073709551616\",\n" +
                    "    \"bignum_large\": \"340282366920938463463374607431768211456\",\n" +
                    "    \"neg_bignum_small\": \"-18446744073709551617\",\n" +
                    "    \"float_mini\": 3.14,\n" +
                    "    \"float_small\": 3.14159,\n" +
                    // this is actually a very large positive number:
                    // 123400000000000000000000000000000000000000000000000000000000000000000000000000000000000000
                    "    \"float_large\": 1.234e+89,\n" +
                    // a very tiny decimal number:
                    // 0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000001234
                    "    \"float_tiny\": 1.234e-89\n" +
                    "  },\n" +
                    "  \"arrays_with_numbers\": [\n" +
                    "    [1, -2, 3.14],\n" +
                    "    [9007199254740991, 9007199254740992],\n" +
                    // note that these 2 are in quotes because > MAX_SAFE_INTEGER
                    "    [\"18446744073709551616\", \"-18446744073709551617\"]\n" +
                    "  ]\n" +
                    "}");
        samples.add("{\n" +
                    "  \"organization\": {\n" +
                    "    \"name\": \"Tech Innovators ā Inc.\",\n" +
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
                    "}");

        samples.add("{\n" +
                    "  \"english\": {\n" +
                    "    \"userProfile\": {\n" +
                    "      \"personalInfo\": {\n" +
                    "        \"firstName\": \"John\",\n" +
                    "        \"lastName\": \"Smith\",\n" +
                    "        \"dateOfBirth\": \"1990-05-15\",\n" +
                    "        \"address\": {\n" +
                    "          \"street\": \"123 Oak Street\",\n" +
                    "          \"city\": \"London\",\n" +
                    "          \"postalCode\": \"SW1A 1AA\",\n" +
                    "          \"country\": \"United Kingdom\"\n" +
                    "        }\n" +
                    "      },\n" +
                    "      \"preferences\": {\n" +
                    "        \"language\": \"English\",\n" +
                    "        \"currency\": \"GBP\",\n" +
                    "        \"notifications\": {\n" +
                    "          \"email\": true,\n" +
                    "          \"sms\": false,\n" +
                    "          \"marketing\": true\n" +
                    "        }\n" +
                    "      },\n" +
                    "      \"orders\": [\n" +
                    "        {\n" +
                    "          \"orderId\": \"ORD-2024-001\",\n" +
                    "          \"date\": \"2024-03-15\",\n" +
                    "          \"items\": [\"Laptop\", \"Mouse\"],\n" +
                    "          \"total\": 1299.99,\n" +
                    "          \"status\": \"delivered\"\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"dutch\": {\n" +
                    "    \"gebruikersProfiel\": {\n" +
                    "      \"persoonlijkeInfo\": {\n" +
                    "        \"voornaam\": \"Jan\",\n" +
                    "        \"achternaam\": \"de Vries\",\n" +
                    "        \"geboortedatum\": \"1990-05-15\",\n" +
                    "        \"adres\": {\n" +
                    "          \"straat\": \"Hoofdstraat 123\",\n" +
                    "          \"stad\": \"Amsterdam\",\n" +
                    "          \"postcode\": \"1012 AB\",\n" +
                    "          \"land\": \"Nederland\"\n" +
                    "        }\n" +
                    "      },\n" +
                    "      \"voorkeuren\": {\n" +
                    "        \"taal\": \"Nederlands\",\n" +
                    "        \"valuta\": \"EUR\",\n" +
                    "        \"meldingen\": {\n" +
                    "          \"email\": true,\n" +
                    "          \"sms\": false,\n" +
                    "          \"marketing\": true\n" +
                    "        }\n" +
                    "      },\n" +
                    "      \"bestellingen\": [\n" +
                    "        {\n" +
                    "          \"bestellingId\": \"BEST-2024-001\",\n" +
                    "          \"datum\": \"2024-03-15\",\n" +
                    "          \"artikelen\": [\"Laptop\", \"Muis\"],\n" +
                    "          \"totaal\": 1299.99,\n" +
                    "          \"status\": \"geleverd\"\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"french\": {\n" +
                    "    \"profilUtilisateur\": {\n" +
                    "      \"informationsPersonnelles\": {\n" +
                    "        \"prenom\": \"Jean\",\n" +
                    "        \"nom\": \"Dupont\",\n" +
                    "        \"dateDeNaissance\": \"1990-05-15\",\n" +
                    "        \"adresse\": {\n" +
                    "          \"rue\": \"123 Rue de la Paix\",\n" +
                    "          \"ville\": \"Paris\",\n" +
                    "          \"codePostal\": \"75001\",\n" +
                    "          \"pays\": \"France\"\n" +
                    "        }\n" +
                    "      },\n" +
                    "      \"preferences\": {\n" +
                    "        \"langue\": \"Français\",\n" +
                    "        \"devise\": \"EUR\",\n" +
                    "        \"notifications\": {\n" +
                    "          \"email\": true,\n" +
                    "          \"sms\": false,\n" +
                    "          \"marketing\": true\n" +
                    "        }\n" +
                    "      },\n" +
                    "      \"commandes\": [\n" +
                    "        {\n" +
                    "          \"commandeId\": \"CMD-2024-001\",\n" +
                    "          \"date\": \"2024-03-15\",\n" +
                    "          \"articles\": [\"Ordinateur portable\", \"Souris\"],\n" +
                    "          \"total\": 1299.99,\n" +
                    "          \"statut\": \"livré\"\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  }\n" +
                    "}");

        // TODO what if it ends with eg. three new chars?
        samples.add("çkzaq{\"ozationzat\":{\"name\":\"Tech Innovators ā Inc.\",\"founded\":2010,\"headquarters\":{\"street\":\"123 Innovation ā Way\",\"city\":\"San Francisco\",\"state\":\"CA\",\"zipCode\":\"94105\"},\"employees\":[{\"id\":\"E001\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"role\":\"Software Engineer\",\"department\":\"Engineering\",\"skills\":[\"JavaScript\",\"Python\",\"AWS\"],\"contactInfo\":{\"email\":\"john.smith@techinnovators.com\",\"phone\":\"+1-555-123-4567\",\"extension\":101},\"projects\":[{\"name\":\"Cloud Migration\",\"status\":\"in-progress\",\"deadline\":\"2024-12-31\"},{\"name\":\"Mobile App Development\",\"status\":\"completed\",\"deadline\":\"2024-06-30\"}]},{\"id\":\"E002\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"role\":\"Product Manager\",\"department\":\"Product\",\"skills\":[\"Agile\",\"Strategy\",\"User Research\"],\"contactInfo\":{\"email\":\"jane.doe@techinnovators.com\",\"phone\":\"+1-555-123-4568\",\"extension\":102},\"projects\":[{\"name\":\"Market Analysis\",\"status\":\"pending\",\"deadline\":\"2024-09-30\"}]}],\"activeSites\":true,\"revenue\":{\"2022\":5000000,\"2023\":7500000,\"projected2024\":10000000}}}");
        samples.add("atzata{\"ozationzat\":{\"name\":\"Tech Innovators ā Inc.\",\"founded\":2010,\"headquarters\":{\"street\":\"123 Innovation ā Way\",\"city\":\"San Francisco\",\"state\":\"CA\",\"zipCode\":\"94105\"},\"employees\":[{\"id\":\"E001\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"role\":\"Software Engineer\",\"department\":\"Engineering\",\"skills\":[\"JavaScript\",\"Python\",\"AWS\"],\"contactInfo\":{\"email\":\"john.smith@techinnovators.com\",\"phone\":\"+1-555-123-4567\",\"extension\":101},\"projects\":[{\"name\":\"Cloud Migration\",\"status\":\"in-progress\",\"deadline\":\"2024-12-31\"},{\"name\":\"Mobile App Development\",\"status\":\"completed\",\"deadline\":\"2024-06-30\"}]},{\"id\":\"E002\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"role\":\"Product Manager\",\"department\":\"Product\",\"skills\":[\"Agile\",\"Strategy\",\"User Research\"],\"contactInfo\":{\"email\":\"jane.doe@techinnovators.com\",\"phone\":\"+1-555-123-4568\",\"extension\":102},\"projects\":[{\"name\":\"Market Analysis\",\"status\":\"pending\",\"deadline\":\"2024-09-30\"}]}],\"activeSites\":true,\"revenue\":{\"2022\":5000000,\"2023\":7500000,\"projected2024\":10000000}}}");
        // test with first two characters = entry in the static dict
        samples.add("\":atzata{\"ozationzat\":{\"name\":\"Tech Innovators ā Inc.\",\"founded\":2010,\"headquarters\":{\"street\":\"123 Innovation ā Way\",\"city\":\"San Francisco\",\"state\":\"CA\",\"zipCode\":\"94105\"},\"employees\":[{\"id\":\"E001\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"role\":\"Software Engineer\",\"department\":\"Engineering\",\"skills\":[\"JavaScript\",\"Python\",\"AWS\"],\"contactInfo\":{\"email\":\"john.smith@techinnovators.com\",\"phone\":\"+1-555-123-4567\",\"extension\":101},\"projects\":[{\"name\":\"Cloud Migration\",\"status\":\"in-progress\",\"deadline\":\"2024-12-31\"},{\"name\":\"Mobile App Development\",\"status\":\"completed\",\"deadline\":\"2024-06-30\"}]},{\"id\":\"E002\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"role\":\"Product Manager\",\"department\":\"Product\",\"skills\":[\"Agile\",\"Strategy\",\"User Research\"],\"contactInfo\":{\"email\":\"jane.doe@techinnovators.com\",\"phone\":\"+1-555-123-4568\",\"extension\":102},\"projects\":[{\"name\":\"Market Analysis\",\"status\":\"pending\",\"deadline\":\"2024-09-30\"}]}],\"activeSites\":true,\"revenue\":{\"2022\":5000000,\"2023\":7500000,\"projected2024\":10000000}}}");
        samples.add("zz");
        samples.add("azq azq azq ");
        samples.add("ab?def?ab?ab?ghi\000\001abc");
        samples.add("?ab?def?ab?ab?ghi\000\001abc");
        //
        samples.add("zqAAAABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
        samples.add("zqABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");

        // TODO long sample has padding error
        samples.add("{\"id\":123,\"name\":\"ā test ā test\",\"email\":\"john.smith@techinnovators.com\"}");
        samples.add("z{\"id\":123,\"name\":\"ā test test ā\",\"email\":\"john.smith@techinnovators.com\"}");
        samples.add("{\"id\":123,\"name\":\"ā test test ā z\",\"email\":\"john.smith@techinnovators.com\"}");
        samples.add("BANANA");
        samples.add("BANANA BANANA NABAN");

        // Null string: Tests boundary case of null
        samples.add(null);
        // Empty string: Tests boundary case of empty input
        samples.add("");
        // Single character: Tests minimal input case
        samples.add("A");
        // Two characters: Tests basic dictionary growth
        samples.add("AB");
        // Repeated character: Tests the special case where code equals current_code
        samples.add("AAAABB");
        // Dictionary filler: Forces dictionary to grow to maximum size (4096 entries)
        // Repeating pattern: Tests pattern recognition with "TOBE" repeated
        samples.add("TOBETOBETOBETOBE");
        // Pattern break: Tests how algorithm handles pattern interruption
        // All ASCII: Tests all possible initial dictionary entries
        // Alternating patterns: Tests dictionary handling of switching patterns
        // Mixed content: Tests handling of mixed case and special characters

        samples.add("āzA{");
        samples.add("0123456789");

        ObjectMapper objectMapper = new ObjectMapper();
        boolean allOkay = true;
        double lzRatioSum = 0;
        int lzRatioNum = 0;
        for (String sample : samples) {

            // normalize the json
            boolean isJson = false;
            if (sample != null && (sample.trim().startsWith("{") || sample.trim().startsWith("["))) {
                sample = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(sample));
                isJson = true;
            }

            Logger.log(sample);

            Object toEncode = isJson ? objectMapper.readValue(sample, new TypeReference<>()
            {
            }) : sample;

            StringBuilder base62 = new StringBuilder();
            BaseXStream.Encoder outputStream = new BaseXStream.Encoder(base62::append, BaseXStream.DEFAULT_CONFIG);
            // needs to be byte aligned so we can write out compressed strings as byte arrays
            LZW encCompressor = new LZW(LZW.DEFAULT_CONFIG);

            IOContext writeContext = new IOContext(StreamReadConstraints.defaults(),
                                                   StreamWriteConstraints.defaults(),
                                                   ErrorReportConfiguration.defaults(),
                                                   objectMapper.getFactory()._getBufferRecycler(),
                                                   ContentReference.rawReference(outputStream),
                                                   false);

            if (isJson) {

                BborGenerator bborGenerator = new BborGenerator(objectMapper,
                                                                writeContext,
                                                                objectMapper.getFactory().getFactoryFeatures(),
                                                                new Bbor.Encoder(Bbor.DEFAULT_CONFIG),
                                                                encCompressor
                );

                // note that the objectMapper calls generator.flush()
                objectMapper.writeTree(bborGenerator, objectMapper.readTree(sample));
                //objectMapper.writeValue(bborGenerator, toEncode);
            }
            else {
                new Bbor.Encoder(Bbor.DEFAULT_CONFIG).write(outputStream, encCompressor, toEncode);
                outputStream.flush();
            }

            // pre-jackson style
            //            new Bbor.Encoder(Bbor.DEFAULT_CONFIG).write(outputStream, encCompressor, toEncode);
            //            outputStream.flush();

            String cbor62 = base62.toString();
            Logger.log(cbor62);

            try {
                // let's test for good and create a new instance
                LZW decCompressor = new LZW(LZW.DEFAULT_CONFIG);

                BaseXStream.Decoder inputStream = new BaseXStream.Decoder(cbor62, BaseXStream.DEFAULT_CONFIG);

                IOContext readContext = new IOContext(StreamReadConstraints.defaults(),
                                                      StreamWriteConstraints.defaults(),
                                                      ErrorReportConfiguration.defaults(),
                                                      objectMapper.getFactory()._getBufferRecycler(),
                                                      ContentReference.rawReference(inputStream),
                                                      false);

                BborParser bborParser = new BborParser(objectMapper,
                                                       readContext,
                                                       objectMapper.getFactory().getFactoryFeatures(),
                                                       new Bbor.Decoder(Bbor.DEFAULT_CONFIG),
                                                       decCompressor
                );

                // test for parsing to a POJO
//                if (isJson) {
//                    TestPojo result = objectMapper.readerFor(TestPojo.class).readValue(bborParser);
//                    Logger.log(result);
//                }

                //Object testJson = objectMapper.readTree(bborParser);
                String decodedSample = isJson ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(bborParser))
                                              : (String) new Bbor.Decoder(Bbor.DEFAULT_CONFIG).read(inputStream, decCompressor);

                // pre-jackson style
                //                Object cborDecoded = new Bbor.Decoder(Bbor.DEFAULT_CONFIG).readValue(base62Decoder, decCompressor);
                //                String decodedSample = isJson ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cborDecoded) : (String) cborDecoded;

                Logger.log(decodedSample);

                if ((sample == null && decodedSample == null) || (sample != null && sample.equals(decodedSample))) {
                    float ratio = (float) cbor62.length() / (sample != null && !sample.isEmpty() ? sample.length() : 1);
                    Logger.log("YES !!! JSON equals JSON, ratio=" + ratio);
                    lzRatioSum += ratio;
                    lzRatioNum++;
                }
                else {
                    Logger.log("ERROR !!! JSON not equals JSON !!! ERROR");
                    allOkay = false;
                }
            }
            catch (Throwable e) {
                Logger.error("Error while decoding CBOR", e);
                allOkay = false;
                break;
            }
        }

        if (allOkay) {
            Logger.log("");
            Logger.log("");
            Logger.log("####################");
            Logger.log("##### ALL OKAY #####");
            Logger.log("####################");
            Logger.log("avg compression ratio: " + (lzRatioSum / lzRatioNum));
        }
        else {
            Logger.error("####################");
            Logger.error("##### !!! ALL NOT OKAY !!! ERROR #####");
            Logger.error("####################");
        }
    }
}
