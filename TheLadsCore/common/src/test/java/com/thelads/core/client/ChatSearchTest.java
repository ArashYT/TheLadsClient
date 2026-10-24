package com.thelads.core.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatSearchTest {
    @BeforeEach
    public void setup() {
        ChatSearch.setActive(false);
        ChatSearch.setQuery("");
    }

    @Test
    public void testSearchMatching() {
        // Inactive: matches all
        assertTrue(ChatSearch.matches("Anything"));

        ChatSearch.setActive(true);
        ChatSearch.setQuery("findme");

        assertTrue(ChatSearch.matches("Please FindMe in this text"));
        assertFalse(ChatSearch.matches("Nothing here"));

        ChatSearch.setQuery("");
        assertTrue(ChatSearch.matches("Anything when query empty"));
    }
}
