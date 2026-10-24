package com.thelads.core.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatShortcutsTest {
    @BeforeEach
    public void setup() {
        ChatShortcuts.resetDefaults();
    }

    @Test
    public void testExpansion() {
        assertEquals("/lobby", ChatShortcuts.expand("/l"));
        assertEquals("/lobby", ChatShortcuts.expand("/hub"));
        assertEquals("/gamemode survival", ChatShortcuts.expand("/gm0"));
        assertEquals("/gamemode creative", ChatShortcuts.expand("/gm1"));
        assertEquals("/friend wrap Player1", ChatShortcuts.expand("/fw Player1"));
        assertEquals("Not a command", ChatShortcuts.expand("Not a command"));
    }
}
