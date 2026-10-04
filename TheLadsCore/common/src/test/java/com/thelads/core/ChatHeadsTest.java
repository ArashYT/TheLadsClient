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

    /** Code points (formatting codes not counted) before the sender's name: where Before name draws the head. */
    private int at(String text) { return ChatHeads.match(text, names).at(); }

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

    @Test void headGoesBeforeTheSendersName() {
        assertEquals(1, at("<Steve> hi Alex_"));
        assertEquals(0, at("Alex_ joined the game"));
        assertEquals(9, at("[12:34] <Steve> hi"), "after the timestamp");
        assertEquals(8, at("§c[Admin] §fSteve§7: §fhello Steve2"), "after the rank, colour codes not counted");
        assertEquals(7, at("[VIP+] Steve » hi"));
        assertEquals(4, at("VIP+Steve: hi"));
        assertEquals(14, at("§7[12:34] §6[MVP] Notchy » hi Steve"), "timestamp, rank and display name");
        assertEquals(3, at("😀 <Jebby> an emoji is one code point"));
        assertEquals("Steve", ChatHeads.match("<STEVE> case", names).player());
        assertNull(ChatHeads.match("Server restarting", names));
    }

    @Test void splitFindsTheHeadInADrawnLineWithFormattingCodes() {
        String line = "§7[12:34] §r§c[Admin] §fSteve§7: §fhi";
        assertEquals("Steve§7: §fhi", line.substring(ChatHeads.split(line, at(line))));
        assertEquals(0, ChatHeads.split("<Steve> hi", 0));
        assertEquals(1, ChatHeads.split("<Steve> hi", 1));
        assertEquals(0, ChatHeads.split("§c[VIP] ", 8), "the name is not on this line: the head goes at the start");
        assertEquals(3, ChatHeads.split("😀 <Jeb", 2), "an emoji is two chars, one code point");
        assertEquals(6, ChatHeads.visibleLength("§c[§lVIP] §r"));
    }

    @Test void positionBeforeNameIsTheDefaultAndIgnoresKeepTextAligned() {
        var before = ConfigManager.toJson();
        try {
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"Chat Heads\":{\"enabled\":true,\"options\":{\"Keep text aligned\":true}}}}").getAsJsonObject());
            com.thelads.core.config.ModuleManager.getInstance().getModule(ChatHeads.NAME).getOption(ChatHeads.POSITION).reset();
            ChatHeads.layoutChanged();
            assertTrue(ChatHeads.beforeName());
            assertEquals(ChatHeads.WIDTH, ChatHeads.offset(true));
            assertEquals(0, ChatHeads.offset(false), "Keep text aligned is for Start of line");
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"Chat Heads\":{\"options\":{\"Position\":1}}}}").getAsJsonObject());
            assertFalse(ChatHeads.beforeName());
            assertEquals(ChatHeads.WIDTH, ChatHeads.offset(false));
            assertTrue(ChatHeads.layoutChanged(), "a new position wraps chat again");
        } finally { ConfigManager.applyJson(before); }
    }

    @Test void offsetFollowsTheModuleAndKeepTextAligned() {
        var before = ConfigManager.toJson();
        try {
            ConfigManager.applyJson(JsonParser.parseString("{\"modules\":{\"Chat Heads\":{\"enabled\":true,\"options\":{\"Keep text aligned\":false,\"Position\":1}}}}").getAsJsonObject());
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
