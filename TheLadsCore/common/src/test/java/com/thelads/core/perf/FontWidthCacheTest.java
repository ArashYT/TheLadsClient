package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FontWidthCacheTest {
    @BeforeEach
    void setUp() {
        FontWidthCache.clear();
        FontWidthCache.setDisabled(false);
    }

    @Test
    void cachesWidthsAndHandlesMisses() {
        assertEquals(-1, FontWidthCache.get("Hello"));
        FontWidthCache.put("Hello", 25);
        assertEquals(25, FontWidthCache.get("Hello"));
        assertEquals(1, FontWidthCache.size());

        FontWidthCache.put("World", 30);
        assertEquals(30, FontWidthCache.get("World"));
        assertEquals(2, FontWidthCache.size());
    }

    @Test
    void clearFlushesCache() {
        FontWidthCache.put("Testing", 42);
        assertEquals(42, FontWidthCache.get("Testing"));
        FontWidthCache.clear();
        assertEquals(-1, FontWidthCache.get("Testing"));
        assertEquals(0, FontWidthCache.size());
    }

    @Test
    void boundsSizeUnderCap() {
        for (int i = 0; i < 5005; i++) {
            FontWidthCache.put("string_" + i, i % 100);
        }
        assertTrue(FontWidthCache.size() <= FontWidthCache.MAX_ENTRIES);
    }

    @Test
    void ignoresNullOrNegative() {
        FontWidthCache.put(null, 10);
        FontWidthCache.put("Bad", -1);
        assertEquals(-1, FontWidthCache.get(null));
        assertEquals(-1, FontWidthCache.get("Bad"));
    }
}
