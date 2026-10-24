package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatPeekTest {
    @Test
    public void testPeekState() {
        ChatPeek.setPeeking(true);
        // Note: isPeeking checks both peeking state and module option
        ChatPeek.setPeeking(false);
        assertFalse(ChatPeek.isPeeking());
    }
}
