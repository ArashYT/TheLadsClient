package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChatTranslatorTest {
    @Test void readsGoogleAnswers() {
        assertEquals("hello my friends", ChatTranslator.parse("[[\"hello my friends\",\"fr\"]]", "bonjour mes amis", "en"));
        assertSame("", ChatTranslator.parse("[[\"hello friends\",\"en\"]]", "hello friends", "en"), "already in English: nothing shown");
        assertSame("", ChatTranslator.parse("[[\"ni hao\",\"zh-CN\"]]", "ni hao", "zh-CN"));
        assertEquals("hola", ChatTranslator.parse("[\"hola\"]", "hello", "es"));
        assertNull(ChatTranslator.parse("<html>429</html>", "x", "en"));
        assertNull(ChatTranslator.parse("[]", "x", "en"));
    }

    @Test void gameLanguages() {
        assertEquals("es", ChatTranslator.fromGame("es_mx"));
        assertEquals("pt", ChatTranslator.fromGame("pt_BR"));
        assertEquals("zh-CN", ChatTranslator.fromGame("zh_cn"));
        assertEquals("zh-TW", ChatTranslator.fromGame("zh_tw"));
        assertEquals("en", ChatTranslator.fromGame(null));
        assertNull(ChatTranslator.code(0), "Game Language");
        assertEquals("fr", ChatTranslator.code(java.util.Arrays.asList(ChatTranslator.CODES).indexOf("fr")));
        assertEquals(ChatTranslator.CODES.length, ChatTranslator.NAMES.length);
    }

    @Test void onlyTextWorthTranslating() {
        assertFalse(ChatTranslator.wanted("12345 !!"));
        assertFalse(ChatTranslator.wanted("x".repeat(ChatTranslator.MAX_LENGTH + 1)));
        assertTrue(ChatTranslator.wanted("hola amigo"));
    }
}
