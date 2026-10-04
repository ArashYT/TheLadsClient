package com.thelads.core.client;

import dzwdz.chat_heads.ChatHeads;
import dzwdz.chat_heads.mixininterface.HeadRenderable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatHistoryTest {
    /** Messages newest first; each splits into "<message>.<line>" lines. Laid out lines run newest first, a message's last line first. */
    @Test void laysOutOlderMessagesOnlyAsFarAsAsked() {
        List<String> messages = List.of("c", "b", "hidden", "a");
        List<String> lines = new ArrayList<>(List.of("d.1", "d.0"));
        int laidOut = ChatHistory.layOut(messages, 0, lines, 5, m -> !m.equals("hidden"), m -> List.of(m + ".0", m + ".1"));
        assertEquals(List.of("d.1", "d.0", "c.1", "c.0", "b.1", "b.0"), lines);
        assertEquals(2, laidOut);
        laidOut = ChatHistory.layOut(messages, laidOut, lines, 100, m -> !m.equals("hidden"), m -> List.of(m + ".0", m + ".1"));
        assertEquals(List.of("d.1", "d.0", "c.1", "c.0", "b.1", "b.0", "a.1", "a.0"), lines);
        assertEquals(4, laidOut);
    }

    private record Message(String text, String head) implements HeadRenderable {
        @Override public Object chatheads$getHeadData() { return head; }
    }

    /** Lazy layout, even inside a new message's addMessageToDisplayQueue scroll, gives each message's first line its own Chat Heads head. */
    @Test void lazyLayoutKeepsEachMessagesChatHead() {
        ChatHeads.lineData = "newest";
        List<String> lines = new ArrayList<>();
        ChatHistory.layOut(List.of(new Message("b", "bob"), new Message("a", "ann")), 0, lines, 10, m -> true,
            m -> List.of(m.text() + ".0 " + ChatHeads.newLine(), m.text() + ".1 " + ChatHeads.newLine()));
        assertEquals(List.of("b.1 EMPTY", "b.0 bob", "a.1 EMPTY", "a.0 ann"), lines);
        assertEquals("newest", ChatHeads.lineData);
        assertFalse(ChatHeads.refreshing);
        ChatHeads.lineData = ChatHeads.EMPTY;
    }
}
