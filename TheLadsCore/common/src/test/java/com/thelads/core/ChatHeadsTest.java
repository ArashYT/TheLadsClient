package com.thelads.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.thelads.core.client.ChatHeads;
import com.thelads.core.config.ConfigManager;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.*;

class ChatHeadsTest {
    private final Map<String, String> names = new LinkedHashMap<>();

    private void player(String name, String displayName, Map<String, String> nicknames) {
        ChatHeads.names(names, name, name, displayName, nicknames);
    }

    @BeforeEach void tabList() {
        player("Steve", null, Map.of());
        player("Steve2", null, Map.of());
        player("Alex_", null, Map.of());
        player("xX_Notch_Xx", "§6Notchy", Map.of());
        player("Jeb", null, Map.of("jeb", "Jebby"));
    }

    private String sender(String text) { return ChatHeads.sender(text, names); }

    @Test void vanillaChatAndTimestamps() {
        assertEquals("Steve", sender("<Steve> hi Alex_"));
        assertEquals("Steve", sender("[12:34] <Steve> hi"));
        assertEquals("Alex_", sender("Alex_ joined the game"));
    }

    @Test void ranksPrefixesAndColourCodesBeforeTheSender() {
        assertEquals("Steve", sender("§c[Admin] §fSteve§7: §fhello Steve2"));
        assertEquals("Steve", sender("[VIP+] Steve » hi"));
        assertEquals("Steve", sender("VIP+Steve: hi"));
        assertEquals("Steve", sender("STEVE: shouting"));
        assertEquals("Steve", sender("§cSt§leve: a name split by a formatting code"));
    }

    @Test void similarNamesMatchOnlyAsWholeWords() {
        assertEquals("Steve2", sender("Steve2: hello Steve"));
        assertEquals("Alex_", sender("<Alex_> hi"));
        assertNull(sender("Steve22 left the game"));
        assertNull(sender("xSteve: hi"));
        assertNull(sender("Alex: underscore matters"));
    }

    @Test void displayNamesAndLadsNicknames() {
        assertEquals("xX_Notch_Xx", sender("[MVP] Notchy » hi Steve"));
        assertEquals("xX_Notch_Xx", sender("<xX_Notch_Xx> the account name still works"));
        assertEquals("Jeb", sender("<Jebby> chat already shows the Lads nickname"));
    }

    @Test void longestNameWinsAtTheSameSpot() {
        player("Builder", "Steve the Builder", Map.of());
        assertEquals("Builder", sender("Steve the Builder: my display name starts with Steve"));
        assertEquals("Steve", sender("Steve: the shorter name alone"));
    }

    @Test void noSenderWithoutAName() {
        assertNull(sender("Server restarting in 5 minutes"));
        assertNull(ChatHeads.sender("<Steve> hi", Map.of()));
        assertNull(sender(""));
    }

    @Test void firstPlayerKeepsASharedName() {
        player("Other", "Steve", Map.of());
        assertEquals("Steve", sender("<Steve> the account owner keeps its name"));
    }

    @Test void offsetFollowsTheModuleAndKeepTextAligned() {
        var before = ConfigManager.toJson();
        try {
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"Chat Heads\":{\"enabled\":true,\"options\":{\"Keep text aligned\":false}}}}").getAsJsonObject());
            ChatHeads.layoutChanged();
            assertEquals(ChatHeads.WIDTH, ChatHeads.offset(true));
            assertEquals(0, ChatHeads.offset(false));
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"Chat Heads\":{\"options\":{\"Keep text aligned\":true}}}}").getAsJsonObject());
            assertEquals(ChatHeads.WIDTH, ChatHeads.offset(false));
            assertTrue(ChatHeads.layoutChanged());
            assertFalse(ChatHeads.layoutChanged());
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"Chat Heads\":{\"enabled\":false}}}").getAsJsonObject());
            assertEquals(0, ChatHeads.offset(true));
            assertTrue(ChatHeads.layoutChanged());
        } finally { ConfigManager.applyJson(before); }
    }
}
