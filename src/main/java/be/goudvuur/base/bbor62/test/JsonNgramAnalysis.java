/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.test;

import be.goudvuur.base.bbor62.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class JsonNgramAnalysis
{
    public static void main(String[] args) throws JsonProcessingException
    {
        String sample1 = "{\n" +
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
                         "      \"projected2024\": 10000000\n" +
                         "    }\n" +
                         "  }\n" +
                         "}";

        ObjectMapper objectMapper = new ObjectMapper();
        String sample2 = objectMapper.writeValueAsString(objectMapper.readTree(sample1));

        // Combine both samples for analysis
        //String combinedText = sample1 + sample2;
        String combinedText = sample2;

        // Get frequency maps
        Map<String, Integer> twoCharFreq = analyzeNGrams(combinedText, 2);
        Map<String, Integer> threeCharFreq = analyzeNGrams(combinedText, 3);
        Map<String, Integer> fourCharFreq = analyzeNGrams(combinedText, 4);

        // Convert to sorted arrays
        String[] sortedTwoChar = getSortedCombinations(twoCharFreq);
        String[] sortedThreeChar = getSortedCombinations(threeCharFreq);
        String[] sortedFourChar = getSortedCombinations(fourCharFreq);

        // Print results
        Logger.log("\n----- Top 20 Two-Character Combinations -----");
        printTop(sortedTwoChar, twoCharFreq, 20);

        Logger.log("\n----- Top 20 Three-Character Combinations -----");
        printTop(sortedThreeChar, threeCharFreq, 20);

        Logger.log("\n----- Top 20 Four-Character Combinations -----");
        printTop(sortedFourChar, fourCharFreq, 20);
    }

    private static Map<String, Integer> analyzeNGrams(String text, int n) {
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (int i = 0; i <= text.length() - n; i++) {
            String combination = text.substring(i, i + n);
            frequencyMap.merge(combination, 1, Integer::sum);
        }

        return frequencyMap;
    }

    private static String[] getSortedCombinations(Map<String, Integer> frequencyMap) {
        return frequencyMap.entrySet().stream()
                           .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                                            .thenComparing(Map.Entry.comparingByKey()))
                           .map(Map.Entry::getKey)
                           .toArray(String[]::new);
    }

    private static void printTop(String[] sorted, Map<String, Integer> frequencyMap, int limit) {
        int count = 0;
        for (String combination : sorted) {
            if (count >= limit) break;
            System.out.printf("|%s| -> %d occurrences%n",
                              combination, frequencyMap.get(combination));
            count++;
        }
    }
}