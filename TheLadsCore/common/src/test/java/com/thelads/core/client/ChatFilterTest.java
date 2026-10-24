package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatFilterTest {
    @Test
    public void testMentionMatching() {
        // Mentions work with word boundaries
        assertTrue(ChatFilter.isMentioned("Hey Player1 how are you?", "Player1"));
        assertTrue(ChatFilter.isMentioned("PLAYER1!", "Player1"));
        assertFalse(ChatFilter.isMentioned("Hello Player123", "Player1"));
        assertFalse(ChatFilter.isMentioned(null, "Player1"));
        assertFalse(ChatFilter.isMentioned("Hello", null));
    }

    @Test
    public void testProfanityFilter() {
        // Note: filterProfanity checks profanityFilterEnabled()
        assertNotNull(ChatFilter.filterProfanity("clean text"));
    }
}
