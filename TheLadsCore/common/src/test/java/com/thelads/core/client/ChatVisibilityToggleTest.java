package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatVisibilityToggleTest {
    @Test
    public void testToggle() {
        ChatVisibilityToggle.setChatHidden(false);
        assertFalse(ChatVisibilityToggle.isChatHidden());
        assertTrue(ChatVisibilityToggle.toggle());
        assertTrue(ChatVisibilityToggle.isChatHidden());
        assertFalse(ChatVisibilityToggle.toggle());
        assertFalse(ChatVisibilityToggle.isChatHidden());
    }
}
