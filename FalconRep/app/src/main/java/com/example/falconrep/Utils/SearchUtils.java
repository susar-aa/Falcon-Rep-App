package com.example.falconrep.utils;

import java.util.HashSet;
import java.util.Set;
import android.text.TextUtils;

public class SearchUtils {

    /**
     * Generates a "Fuzzy Token String" for storage in the database.
     * Logic:
     * 1. Original text (for exact matching)
     * 2. Consonant Skeleton (e.g. "Pencil" -> "PNCL") to match typos like "Pencel"
     * 3. SKU (cleaned)
     */
    public static String generateSearchTokens(String name, String sku, String categoryTokens) {
        Set<String> tokens = new HashSet<>();

        // 1. Add SKU (Cleaned)
        if (sku != null && !sku.isEmpty()) {
            tokens.add(sku.toLowerCase().trim());
            // Add SKU without dashes/spaces if it has them (e.g., "AB-123" -> "AB123")
            String cleanSku = sku.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (!cleanSku.isEmpty()) tokens.add(cleanSku);
        }

        // 2. Add Name Parts & Fuzzy Skeletons
        if (name != null) {
            String[] words = name.toLowerCase().split("\\s+");
            for (String w : words) {
                // Remove non-alphanumeric chars
                String clean = w.replaceAll("[^a-z0-9]", "");
                if (clean.isEmpty()) continue;

                tokens.add(clean); // Add exact word ("pencil")

                // Generate Consonant Skeleton ("pencil" -> "pncl")
                String skeleton = getConsonantSkeleton(clean);
                if (skeleton.length() > 1) { // Avoid single letters
                    tokens.add(skeleton);
                }
            }
        }

        // 3. Add Category Tokens
        if (categoryTokens != null) {
            tokens.add(categoryTokens);
        }

        return TextUtils.join(" ", tokens);
    }

    /**
     * Prepares the user's query for searching.
     * Converts "Pencel" -> "Pencel* OR pncl*"
     */
    public static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) return "";

        String[] words = rawQuery.toLowerCase().trim().split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (String w : words) {
            String clean = w.replaceAll("[^a-z0-9]", "");
            if (clean.isEmpty()) continue;

            if (sb.length() > 0) sb.append(" AND ");

            // Logic: Match (Raw*) OR (Skeleton*)
            String skeleton = getConsonantSkeleton(clean);

            sb.append("(");
            sb.append(clean).append("*"); // Standard Prefix Match

            if (skeleton.length() > 1 && !skeleton.equals(clean)) {
                sb.append(" OR ").append(skeleton).append("*"); // Fuzzy Match
            }
            sb.append(")");
        }

        return sb.toString();
    }

    private static String getConsonantSkeleton(String input) {
        if (input == null) return "";
        // Keep first letter, then remove vowels a,e,i,o,u
        // This is a simplified Soundex/Metaphone approach
        String first = input.substring(0, 1);
        String rest = input.substring(1).replaceAll("[aeiou]", "");
        return first + rest;
    }
}