package com.thelads.core.client.colors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomColorsTest {
    @BeforeEach
    void setUp() {
        CustomColors.clear();
    }

    @Test
    void testLoadProperties() {
        String props = "text.code.0=000000\n" +
                "text.code.15=FFFFFF\n" +
                "drop.wool.red=FF0000\n";

        CustomColors.loadProperties(props);
        assertTrue(CustomColors.hasColor("text.code.0"));
        assertTrue(CustomColors.hasColor("drop.wool.red"));
        assertFalse(CustomColors.hasColor("drop.wool.blue"));

        assertEquals(0x000000, CustomColors.getColor("text.code.0", -1));
        assertEquals(0xFFFFFF, CustomColors.getColor("text.code.15", -1));
        assertEquals(0xFF0000, CustomColors.getColor("drop.wool.red", -1));
        assertEquals(0x123456, CustomColors.getColor("unknown", 0x123456));
    }
}
