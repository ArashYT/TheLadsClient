package com.thelads.core.client;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
