/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.test;

import be.goudvuur.base.bbor62.BaseXStream;
import be.goudvuur.base.bbor62.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created by bram on Nov 05, 2024
 */
public class Base62Test
{
    public static void main(String[] args) throws IOException
    {
        String[] testStrings = {

                        // Create test data
                        "12345",

                        // "??>" consists of:
                        // Two question marks (ASCII 63, binary 00111111)
                        // One greater-than sign (ASCII 62, binary 00111110)
                        //  001111
                        //  110011
                        //  111100
                        //  111110
                        //
                        // The last chunk is exactly 111110 as requested!
                        "??>",

                        // Each ? is ASCII 63, which in binary is 00111111
                        // When we concatenate these three bytes, we get: 001111110011111100111111
                        // When we repack this into 6-bit chunks, we get:
                        //  001111
                        //  110011
                        //  111100
                        //  111111
                        // -> The last chunk is exactly 111111 as requested!
                        "???",

                        ">>>>",

                        "john.smith@techinnovators.com",

                        "zq{\"organization\":{\"name\":\"Tech Innovators ā Inc.\",\"founded\":2010,\"headquarters\":{\"street\":\"123 Innovation Way\",\"city\":\"San Francisco\",\"state\":\"CA\",\"zipCode\":\"94105\"},\"employees\":[{\"id\":\"E001\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"role\":\"Software Engineer\",\"department\":\"Engineering\",\"skills\":[\"JavaScript\",\"Python\",\"AWS\"],\"contactInfo\":{\"email\":\"john.smith@techinnovators.com",

                        "zq{\"organization\":{\"name\":\"Tech Innovators ā Inc.\",\"founded\":2010,\"headquarters\":{\"street\":\"123 Innovation Way\",\"city\":\"San Francisco\",\"state\":\"CA\",\"zipCode\":\"94105\"},\"employees\":[{\"id\":\"E001\",\"firstName\":\"John\",\"lastName\":\"Smith\",\"role\":\"Software Engineer\",\"department\":\"Engineering\",\"skills\":[\"JavaScript\",\"Python\",\"AWS\"],\"contactInfo\":{\"email\":\"john.smith@techinnovators.com\",\"phone\":\"+1-555-123-4567\",\"extension\":101},\"projects\":[{\"name\":\"Cloud Migration\",\"status\":\"in-progress\",\"deadline\":\"2024-12-31\"},{\"name\":\"Mobile App Development\",\"status\":\"completed\",\"deadline\":\"2024-06-30\"}]},{\"id\":\"E002\",\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"role\":\"Product Manager\",\"department\":\"Product\",\"skills\":[\"Agile\",\"Strategy\",\"User Research\"],\"contactInfo\":{\"email\":\"jane.doe@techinnovators.com\",\"phone\":\"+1-555-123-4568\",\"extension\":102},\"projects\":[{\"name\":\"Market Analysis\",\"status\":\"pending\",\"deadline\":\"2024-09-30\"}]}],\"activeSites\":true,\"revenue\":{\"2022\":5000000,\"2023\":7500000,\"projected2024\":10000000}}}",

                        "ab?def?ab?ab?ghi\000\001abc",

                        "?ab?def?ab?ab?ghi\000\001abc",

                        };

        double ratios = 0;
        for (String testData : testStrings) {

            StringBuilder base62 = new StringBuilder();

            BaseXStream.Encoder encoder = new BaseXStream.Encoder(base62::append, BaseXStream.DEFAULT_CONFIG);
            // Write values
            Logger.log("Writing value: " + testData);
            for (int i = 0; i < testData.length(); i++) {
                byte[] charBytes = String.valueOf(testData.charAt(i)).getBytes(StandardCharsets.UTF_8);
                for (byte c : charBytes) {
                    encoder.write(c, 8);
                }
            }
            encoder.flush();

            // Convert to string
            String encoded = base62.toString();
            Logger.log("\nEncoded: " + encoded);

            // Create new stream and decode
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BaseXStream.Decoder decoder = new BaseXStream.Decoder(encoded, BaseXStream.DEFAULT_CONFIG);
            while (decoder.hasNext(8)) {
                outputStream.write((byte) decoder.read(8));
            }

            // Read values back
            String decoded = outputStream.toString(StandardCharsets.UTF_8);
            Logger.log("\nReading value back: " + decoded);

            float ratio = ((float) encoded.length() / testData.length());
            ratios += ratio;

            if (testData.equals(decoded)) {
                Logger.log("\nSUCCESS!!! -> " + ratio);
            }
            else {
                Logger.log("\nERROR!!!");
            }
        }

        Logger.log("Mean ratio: " + (ratios / testStrings.length));

        //        ByteBuffer test1 = ByteBuffer.allocate(24);
        //        test1.put((byte) Integer.parseInt(StringUtils.repeat('1', 8), 2));
        //        test1.put((byte) Integer.parseInt(StringUtils.repeat('1', 8), 2));
        //        test1.put((byte) Integer.parseInt(StringUtils.repeat('1', 8), 2));
        //
        //        Base62Encoder encoder = new Base62Encoder();
        //        encoder.write(255, 8);
        //        encoder.write(255, 8);
        //        encoder.write(255, 8);
        //        encoder.write(255, 8);
        //        encoder.write(255, 8);
        //        encoder.write(255, 5);
        //        // --> 25
        //
        //        String encoded = encoder.finish();
        //        Logger.log("\nEncoded: " + encoded);
        //
        //        Base62Decoder decoder = new Base62Decoder();
        //        for (int i = 0; i < encoded.length(); i++) {
        //            decoder.read(encoded.charAt(i));
        //        }
        //        byte[] decoded = decoder.finish();
        //        Logger.log("\nDecoded: " + decoded);
    }
}
