package com.thelads.core;

import com.thelads.core.client.RomanNumerals;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RomanNumeralsTest {
    @Test
    void testParseRoman() {
        assertEquals(1, RomanNumerals.parseRoman("I"));
        assertEquals(2, RomanNumerals.parseRoman("II"));
        assertEquals(3, RomanNumerals.parseRoman("III"));
        assertEquals(4, RomanNumerals.parseRoman("IV"));
        assertEquals(5, RomanNumerals.parseRoman("V"));
        assertEquals(6, RomanNumerals.parseRoman("VI"));
        assertEquals(7, RomanNumerals.parseRoman("VII"));
        assertEquals(8, RomanNumerals.parseRoman("VIII"));
        assertEquals(9, RomanNumerals.parseRoman("IX"));
        assertEquals(10, RomanNumerals.parseRoman("X"));
        assertEquals(25, RomanNumerals.parseRoman("XXV"));
    }

    @Test
    void testConvertTrailingRoman() {
        assertEquals("Sharpness 5", RomanNumerals.convertTrailingRoman("Sharpness V"));
        assertEquals("Protection 4", RomanNumerals.convertTrailingRoman("Protection IV"));
        assertEquals("Efficiency 10", RomanNumerals.convertTrailingRoman("Efficiency X"));
        assertEquals("Speed 2", RomanNumerals.convertTrailingRoman("Speed II"));
        assertEquals("Golden Apple", RomanNumerals.convertTrailingRoman("Golden Apple"));
    }
}
