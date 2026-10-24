package com.thelads.core.client;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces Roman numerals in enchantment tooltips and potion effect titles with Arabic numbers.
 */
public final class RomanNumerals {
    private static final Map<String, Integer> ROMAN_TO_ARABIC = new HashMap<>();
    private static final Map<Integer, String> ARABIC_TO_ROMAN = new HashMap<>();

    static {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 1; i <= 50; i++) {
            StringBuilder sb = new StringBuilder();
            int num = i;
            for (int k = 0; k < symbols.length; k++) {
                while (num >= values[k]) {
                    sb.append(symbols[k]);
                    num -= values[k];
                }
            }
            String roman = sb.toString();
            ROMAN_TO_ARABIC.put(roman, i);
            ARABIC_TO_ROMAN.put(i, roman);
        }
    }

    private static final Pattern TRAILING_ROMAN_PATTERN = Pattern.compile("^(.*\\s+)([A-Za-z]+)(\\s*)$");

    private RomanNumerals() {}

    /**
     * Parses standard Roman numerals up to 50. Rejects invalid non-canonical strings or English words like 'mix' or 'civic'.
     */
    public static int parseRoman(String roman) {
        if (roman == null || roman.isEmpty()) return -1;
        Integer arabic = ROMAN_TO_ARABIC.get(roman.toUpperCase(java.util.Locale.ROOT));
        return arabic != null ? arabic : -1;
    }

    public static String toRoman(int arabic) {
        return ARABIC_TO_ROMAN.get(arabic);
    }

    /**
     * Replaces trailing Roman numeral (e.g. "Sharpness V" -> "Sharpness 5", "Protection IV" -> "Protection 4")
     * with standard Arabic number. Only converts valid canonical Roman numerals between 1 and 50.
     */
    public static String convertTrailingRoman(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = TRAILING_ROMAN_PATTERN.matcher(text);
        if (m.matches()) {
            String prefix = m.group(1);
            String roman = m.group(2);
            String suffix = m.group(3);
            int arabic = parseRoman(roman);
            if (arabic > 0 && arabic <= 50) {
                return prefix + arabic + suffix;
            }
        }
        return text;
    }
}
