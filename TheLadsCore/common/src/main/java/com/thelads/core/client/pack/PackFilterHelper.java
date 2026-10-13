package com.thelads.core.client.pack;

import java.util.regex.Pattern;

/** Pure helper for pack name/description filtering. */
public final class PackFilterHelper {
    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("(?i)\u00A7[0-9A-FK-OR]");

    private PackFilterHelper() {}

    public static String stripFormatting(String input) {
        if (input == null) return "";
        return FORMAT_CODE_PATTERN.matcher(input).replaceAll("");
    }

    public static boolean matches(String query, String name, String description) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase();
        if (name != null) {
            String cleanName = stripFormatting(name).toLowerCase();
            if (cleanName.contains(q)) return true;
        }
        if (description != null) {
            String cleanDesc = stripFormatting(description).toLowerCase();
            if (cleanDesc.contains(q)) return true;
        }
        return false;
    }
}
