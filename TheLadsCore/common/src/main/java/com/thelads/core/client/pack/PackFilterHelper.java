package com.thelads.core.client.pack;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Pure helper for pack name/description filtering. */
public final class PackFilterHelper {
    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");
    private static final int MAX_CACHE_SIZE = 4096;
    private static final ConcurrentHashMap<String, String> STRIPPED_LOWER_CACHE = new ConcurrentHashMap<>();

    private PackFilterHelper() {}

    public static String stripFormatting(String input) {
        if (input == null) return "";
        return FORMAT_CODE_PATTERN.matcher(input).replaceAll("");
    }

    private static String stripAndLower(String input) {
        if (input == null || input.isEmpty()) return "";
        String cached = STRIPPED_LOWER_CACHE.get(input);
        if (cached != null) {
            return cached;
        }
        String computed = FORMAT_CODE_PATTERN.matcher(input).replaceAll("").toLowerCase(Locale.ROOT);
        if (STRIPPED_LOWER_CACHE.size() >= MAX_CACHE_SIZE) {
            STRIPPED_LOWER_CACHE.clear();
        }
        STRIPPED_LOWER_CACHE.put(input, computed);
        return computed;
    }

    public static boolean matches(String query, String name, String description) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (name != null) {
            if (stripAndLower(name).contains(q)) return true;
        }
        if (description != null) {
            if (stripAndLower(description).contains(q)) return true;
        }
        return false;
    }
}
