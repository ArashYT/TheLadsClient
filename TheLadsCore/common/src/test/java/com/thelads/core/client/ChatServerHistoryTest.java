package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatServerHistoryTest {
    @BeforeEach void on() { ChatServerHistory.reset(); ChatTweaks.serverHistory = true; }
    @AfterEach void off() { ChatServerHistory.reset(); ChatTweaks.tick(); }

    @Test void disconnectAndRejoinRestores() {
        List<String> chat = new ArrayList<>(List.of("b", "a"));
        assertFalse(ChatServerHistory.joined("one.net", chat), "nothing kept for a first join");
        ChatServerHistory.clearing(chat); // vanilla clears the chat on disconnect
        chat.clear();
        chat.add("lobby");
        assertTrue(ChatServerHistory.joined("one.net", chat));
        assertEquals(List.of("lobby", "b", "a"), chat, "kept messages come back older than the new one");
    }

    @Test void switchingServersSwapsTheChat() {
        List<String> chat = new ArrayList<>(List.of("on one"));
        ChatServerHistory.joined("one.net", chat);
        assertTrue(ChatServerHistory.joined("two.net", chat), "1.8.9 keeps the last server's chat shown: it is put away");
        assertEquals(List.of(), chat);
        chat.add("on two");
        assertTrue(ChatServerHistory.joined("one.net", chat));
        assertEquals(List.of("on one"), chat);
        assertFalse(ChatServerHistory.joined(null, chat), "singleplayer and menus change nothing");
        assertFalse(ChatServerHistory.joined("one.net", chat));
    }

    @Test void offKeepsVanilla() {
        ChatTweaks.serverHistory = false;
        List<String> chat = new ArrayList<>(List.of("x"));
        ChatServerHistory.joined("one.net", chat);
        assertFalse(ChatServerHistory.joined("two.net", chat));
        assertEquals(List.of("x"), chat);
    }
}
