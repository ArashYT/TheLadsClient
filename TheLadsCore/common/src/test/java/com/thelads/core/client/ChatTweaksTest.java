package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatTweaksTest {
    private static Module chat() { return ModuleManager.getInstance().getModule("Chat"); }

    @AfterEach void restore() {
        HypixelSafetyManager.qaOverride = false;
        for (Option option : chat().getOptions()) option.reset();
        chat().setEnabled(true);
        ChatTweaks.tick();
        ChatTweaks.selectTab(0);
        ChatTweaks.setQuery("");
        ChatTweaks.breakRepeat();
    }

    @Test void tabsAreOffByDefaultAndHypixelTabsOnlyOnHypixel() {
        ChatTweaks.tick();
        assertEquals(0, ChatTweaks.tabCount(), "Chat Tabs starts off");
        ((BoolOption) chat().getOption(ChatTweaks.TABS)).set(true);
        ChatTweaks.tick();
        assertEquals(1, ChatTweaks.tabCount(), "off Hypixel only All (no Custom Tabs yet)");
        ((TextOption) chat().getOption(ChatTweaks.CUSTOM_TABS)).setValue("Trades=(?i)trade");
        ChatTweaks.tick();
        assertEquals(2, ChatTweaks.tabCount(), "custom tabs on every server");
        HypixelSafetyManager.qaOverride = true; // Hypixel
        ChatTweaks.tick();
        assertEquals(5, ChatTweaks.tabCount(), "All, Party, Guild, PM, then the custom tab");
        assertEquals("Trades", ChatTweaks.tabName(4));
    }

    @Test void customTabsFilterMessages() {
        ((BoolOption) chat().getOption(ChatTweaks.TABS)).set(true);
        HypixelSafetyManager.qaOverride = true;
        ChatTweaks.tick();
        assertEquals(4, ChatTweaks.tabCount(), "All plus the built-in Party, Guild and PM tabs on Hypixel");
        assertFalse(ChatTweaks.filtering());
        assertTrue(ChatTweaks.selectTab(1));
        assertEquals("Party", ChatTweaks.tabName(1));
        assertTrue(ChatTweaks.shows("Party > [MVP+] Steve: hi"));
        assertFalse(ChatTweaks.shows("Guild > Alex: hi"));
        HypixelSafetyManager.qaOverride = false;
        ((TextOption) chat().getOption(ChatTweaks.CUSTOM_TABS)).setValue("Bad=([;Trades=(?i)trade");
        ChatTweaks.tick();
        assertEquals(2, ChatTweaks.tabCount(), "a tab with a broken pattern is left out");
        assertEquals("Trades", ChatTweaks.tabName(1));
        assertEquals(0, ChatTweaks.activeTab(), "changed tabs go back to All");
    }

    @Test void searchIsCaseInsensitive() {
        ChatTweaks.tick();
        int revision = ChatTweaks.filterRevision;
        assertTrue(ChatTweaks.setQuery("  DiaMond "));
        assertTrue(ChatTweaks.filterRevision != revision);
        assertTrue(ChatTweaks.shows("Selling diamonds"));
        assertFalse(ChatTweaks.shows("Selling gold"));
        assertFalse(ChatTweaks.setQuery("diamond"), "the same search lays nothing out again");
    }

    @Test void tabsOffShowsEverything() {
        ((BoolOption) chat().getOption(ChatTweaks.TABS)).set(true);
        HypixelSafetyManager.qaOverride = true;
        ChatTweaks.tick();
        ChatTweaks.selectTab(2);
        ((BoolOption) chat().getOption(ChatTweaks.TABS)).set(false);
        ChatTweaks.tick();
        assertEquals(0, ChatTweaks.tabCount());
        assertTrue(ChatTweaks.shows("anything"));
    }

    @Test void compactCountsRepeatsInARow() {
        ChatTweaks.tick();
        assertEquals(1, ChatTweaks.repeat("Welcome!", 0));
        assertEquals(2, ChatTweaks.repeat("Welcome!", 1000));
        assertEquals(3, ChatTweaks.repeat("Welcome!", 2000));
        assertEquals(1, ChatTweaks.repeat("Other", 3000));
        assertEquals(1, ChatTweaks.repeat("Welcome!", 4000), "only messages in a row stack");
        assertEquals(1, ChatTweaks.repeat("Welcome!", 4000 + ChatTweaks.COMPACT_WINDOW + 1), "nor after a minute");
        ((BoolOption) chat().getOption(ChatTweaks.COMPACT)).set(false);
        ChatTweaks.tick();
        assertEquals(1, ChatTweaks.repeat("x", 0));
        assertEquals(1, ChatTweaks.repeat("x", 1));
    }

    @Test void ownLinesAreNotMentions() {
        ChatTweaks.tick();
        assertNull(ChatTweaks.marks("<Steve> hello everyone", "Steve"));
        assertNull(ChatTweaks.marks("[MVP+] Steve: hello", "Steve"));
        int[][] marks = ChatTweaks.marks("Alex: hey steve, come here", "Steve");
        assertArrayEquals(new int[] {10, 15}, marks[1]);
        assertNull(ChatTweaks.marks("Steven is here", "Steve"), "a longer name is not the player's");
    }

    @Test void profanityKeepsEveryRunsStyle() {
        ((BoolOption) chat().getOption(ChatTweaks.PROFANITY)).set(true);
        ChatTweaks.tick();
        int[][] marks = ChatTweaks.marks("Steve: what the shit, Alex", "Alex");
        assertTrue(ChatTweaks.rewrites(marks));
        List<String> out = new ArrayList<>();
        List<String> styles = new ArrayList<>();
        List<Boolean> highlighted = new ArrayList<>();
        ChatTweaks.rewrite(marks, List.of("Steve: ", "what the sh", "it, Alex"), List.of("gold", "white", "link"), (text, style, mention) -> {
            out.add(text);
            styles.add(style);
            highlighted.add(mention);
        });
        assertEquals("Steve: what the ****, Alex", String.join("", out));
        assertEquals(List.of("Steve: ", "what the ", "**", "**", ", ", "Alex"), out);
        assertEquals(List.of("gold", "white", "white", "link", "link", "link"), styles);
        assertEquals(List.of(false, false, false, false, false, true), highlighted);
    }

    @Test void mentionSoundIsThrottled() {
        ChatTweaks.tick();
        assertFalse(ChatTweaks.ping(ChatTweaks.marks("hi alex", "Alex"), 1_000), "Mention Sound starts off");
        ((BoolOption) chat().getOption(ChatTweaks.MENTION_SOUND)).set(true);
        ChatTweaks.tick();
        int[][] marks = ChatTweaks.marks("hi alex", "Alex");
        assertTrue(ChatTweaks.ping(marks, 10_000));
        assertFalse(ChatTweaks.ping(marks, 10_500));
        assertTrue(ChatTweaks.ping(marks, 11_000));
        assertFalse(ChatTweaks.ping(null, 50_000));
    }

    @Test void shortcutsExpandTheFirstWord() {
        ((TextOption) chat().getOption(ChatTweaks.SHORTCUTS)).setValue("/l=/lobby; gg = Good game!;bad");
        ChatTweaks.tick();
        assertEquals("/lobby", ChatTweaks.expand("/L"));
        assertEquals("/lobby bedwars", ChatTweaks.expand("/l bedwars"));
        assertEquals("Good game!", ChatTweaks.expand("gg"));
        assertEquals("/list", ChatTweaks.expand("/list"));
        assertEquals("hello gg", ChatTweaks.expand("hello gg"));
        chat().setEnabled(false);
        ChatTweaks.tick();
        assertEquals("/l", ChatTweaks.expand("/l"), "the Chat module off expands nothing");
    }

    @Test void messageButtons() {
        assertEquals(-1, ChatTweaks.button(99, 100));
        assertEquals(0, ChatTweaks.button(100, 100), "Copy starts at the chat's right edge");
        assertEquals(0, ChatTweaks.button(100 + ChatTweaks.BUTTON - 1, 100));
        assertEquals(-1, ChatTweaks.button(100 + ChatTweaks.BUTTON, 100), "gap between Copy and Delete");
        assertEquals(1, ChatTweaks.button(100 + ChatTweaks.BUTTON + 1, 100));
        assertEquals(1, ChatTweaks.button(100 + 2 * ChatTweaks.BUTTON, 100));
        assertEquals(-1, ChatTweaks.button(100 + 2 * ChatTweaks.BUTTON + 1, 100), "gap between Delete and Image");
        assertEquals(2, ChatTweaks.button(100 + 2 * ChatTweaks.BUTTON + 2, 100), "Save as Image button");
        assertEquals(2, ChatTweaks.button(100 + 3 * ChatTweaks.BUTTON + 1, 100));
        assertEquals(-1, ChatTweaks.button(100 + 3 * ChatTweaks.BUTTON + 2, 100));
    }

    @Test void exportChatImageTest() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("lads-chat-export-test");
        java.nio.file.Path file = ChatTweaks.exportChatImage("§a[Server] §fWelcome to §bThe Lads§f!", tempDir).get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(file);
        assertTrue(java.nio.file.Files.exists(file));
        assertTrue(java.nio.file.Files.size(file) > 0);
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
        assertNotNull(img);
        assertTrue(img.getWidth() > 50);
        assertTrue(img.getHeight() > 20);
        java.nio.file.Files.deleteIfExists(file);
        java.nio.file.Files.deleteIfExists(tempDir);
    }

    @Test void fuzzyCommandWords() {
        assertTrue(ChatTweaks.fuzzyMatch("gamemode", "gmd"));
        assertTrue(ChatTweaks.fuzzyMatch("tpaccept", "tpc"));
        assertFalse(ChatTweaks.fuzzyMatch("gamemode", "ga"), "a prefix is vanilla's own suggestion");
        assertFalse(ChatTweaks.fuzzyMatch("gamemode", "md"), "the first letter must match");
        assertFalse(ChatTweaks.fuzzyMatch("gamemode", "gdm"), "letters in order");
        assertFalse(ChatTweaks.fuzzyMatch("give", "g"), "one letter suggests everything already");
    }

    @Test void serverKeys() {
        assertNull(ChatTweaks.serverKey("Singleplayer"));
        assertNull(ChatTweaks.serverKey("Unknown"));
        assertNull(ChatTweaks.serverKey(null));
        assertEquals("mc.hypixel.net", ChatTweaks.serverKey("mc.hypixel.net"));
        assertFalse(ChatTweaks.longCommands(false));
    }
}
