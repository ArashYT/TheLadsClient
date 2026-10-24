package com.thelads.core.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces Roman numerals in enchantment tooltips and potion effect titles with Arabic numbers.
 */
public final class RomanNumerals {
    private static final String[] ROMAN_TABLE = {
        "X", "IX", "VIII", "VII", "VI", "V", "IV", "III", "II", "I"
    };
    private static final int[] ARABIC_TABLE = {
        10, 9, 8, 7, 6, 5, 4, 3, 2, 1
    };

    private static final Pattern TRAILING_ROMAN_PATTERN = Pattern.compile("^(.*\\s+)([IVXLCDMivxlcdm]+)(\\s*)$");

    private RomanNumerals() {}

    public static int parseRoman(String roman) {
        if (roman == null || roman.isEmpty()) return -1;
        String upper = roman.toUpperCase(java.util.Locale.ROOT);
        int result = 0;
        int i = 0;
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

        for (int k = 0; k < symbols.length; k++) {
            while (upper.startsWith(symbols[k], i)) {
                result += values[k];
                i += symbols[k].length();
            }
        }
        return i == upper.length() ? result : -1;
    }

    /**
     * Replaces trailing Roman numeral (e.g. "Sharpness V" -> "Sharpness 5", "Protection IV" -> "Protection 4")
     * with standard Arabic number.
     */
    public static String convertTrailingRoman(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = TRAILING_ROMAN_PATTERN.matcher(text);
        if (m.matches()) {
            String prefix = m.group(1);
            String roman = m.group(2);
            String suffix = m.group(3);
            int arabic = parseRoman(roman);
            if (arabic > 0) {
                return prefix + arabic + suffix;
            }
        }
        return text;
    }
}
