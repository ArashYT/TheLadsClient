package com.thelads.core.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CompactChatTest {
    @BeforeEach
    public void setup() {
        CompactChat.reset();
    }

    @Test
    public void testStripCount() {
        assertEquals("Hello world", CompactChat.stripCount("Hello world §7(x2)"));
        assertEquals("Hello world", CompactChat.stripCount("Hello world (x5)"));
        assertEquals("Hello world", CompactChat.stripCount("Hello world"));
        assertNull(CompactChat.stripCount(null));
    }

    @Test
    public void testFormatStacked() {
        assertEquals("Hello world", CompactChat.formatStacked("Hello world", 1));
        assertEquals("Hello world §7(x2)", CompactChat.formatStacked("Hello world", 2));
        assertEquals("Hello world §7(x10)", CompactChat.formatStacked("Hello world", 10));
    }
}
