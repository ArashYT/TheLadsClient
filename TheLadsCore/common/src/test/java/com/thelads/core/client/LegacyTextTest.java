package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyTextTest {
    @Test void namedColoursMapExactlyAndOthersToTheNearest() {
        assertEquals('c', LegacyText.code(0xFF5555));
        assertEquals('f', LegacyText.code(0xFFFFFF));
        assertEquals('0', LegacyText.code(0x000000));
        assertEquals('6', LegacyText.code(0xFFAA00));
        assertEquals('6', LegacyText.code(0xF0A010)); // a custom gold
        assertEquals('b', LegacyText.code(0x40F0F0)); // a custom aqua
    }
}
