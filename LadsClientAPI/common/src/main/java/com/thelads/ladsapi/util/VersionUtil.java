package com.thelads.ladsapi.util;

/**
 * Utility for comparing dotted version strings such as "1.9.13" vs "1.9.12".
 */
public final class VersionUtil {
    private static final java.util.regex.Pattern PLAIN_VERSION = java.util.regex.Pattern.compile("[0-9A-Za-z.+_-]{1,32}");

    private VersionUtil() {}

    /**
     * Version text a client sent about itself ("1.11.1", "26.3", "1.20.1"): returned as is when it is 1-32 plain characters
     * (letters, digits and . + _ -), otherwise (missing, too long, colour codes, MiniMessage tags, control characters) "unknown".
     */
    public static String clean(String text) {
        return text != null && PLAIN_VERSION.matcher(text).matches() ? text : "unknown";
    }

    /**
     * Compares two dotted version strings.
     * Returns a negative integer, zero, or a positive integer as v1 is less than, equal to, or greater than v2.
     */
    public static int compare(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String[] p1 = v1.trim().split("\\.");
        String[] p2 = v2.trim().split("\\.");
        int maxLen = Math.max(p1.length, p2.length);

        for (int i = 0; i < maxLen; i++) {
            String seg1 = i < p1.length ? p1[i].trim() : "0";
            String seg2 = i < p2.length ? p2[i].trim() : "0";

            int num1 = parseLeadingInt(seg1);
            int num2 = parseLeadingInt(seg2);

            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
            int strCmp = seg1.compareTo(seg2);
            if (strCmp != 0) {
                return strCmp;
            }
        }
        return 0;
    }

    /**
     * Returns true if {@code clientVersion} is older than {@code minVersion}.
     * If minVersion is null or blank, returns false.
     */
    public static boolean isOlder(String clientVersion, String minVersion) {
        if (minVersion == null || minVersion.isBlank()) return false;
        if (clientVersion == null || clientVersion.isBlank()) return true;
        return compare(clientVersion, minVersion) < 0;
    }

    private static int parseLeadingInt(String seg) {
        if (seg.isEmpty()) return 0;
        int end = 0;
        while (end < seg.length() && Character.isDigit(seg.charAt(end))) {
            end++;
        }
        if (end == 0) return 0;
        try {
            return Integer.parseInt(seg.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
