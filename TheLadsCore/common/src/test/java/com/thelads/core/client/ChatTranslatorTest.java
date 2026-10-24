package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatTranslatorTest {
    @Test
    public void testParseGoogleResponse() {
        assertEquals("Bonjour", ChatTranslator.parseGoogleResponse("[\"Bonjour\"]"));
        assertEquals("Hello world", ChatTranslator.parseGoogleResponse("[[\"Hello world\", \"auto\"]]"));
        assertEquals("Simple", ChatTranslator.parseGoogleResponse("\"Simple\""));
        assertNull(ChatTranslator.parseGoogleResponse(null));
    }
}
