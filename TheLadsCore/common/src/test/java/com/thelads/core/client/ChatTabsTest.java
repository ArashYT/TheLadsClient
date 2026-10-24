package com.thelads.core.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatTabsTest {
    @BeforeEach
    public void setup() {
        ChatTabs.resetDefaultTabs();
    }

    @Test
    public void testDefaultTabs() {
        assertTrue(ChatTabs.getTabCount() >= 4);
        assertEquals("ALL", ChatTabs.getTab(0).getName());
        assertEquals("PARTY", ChatTabs.getTab(1).getName());
        assertEquals("GUILD", ChatTabs.getTab(2).getName());
        assertEquals("PMS", ChatTabs.getTab(3).getName());
    }

    @Test
    public void testTabMatching() {
        // ALL matches everything
        assertTrue(ChatTabs.matches(0, "Random chat message"));
        assertTrue(ChatTabs.matches(0, "Party > Player: hi"));

        // PARTY
        assertTrue(ChatTabs.matches(1, "Party > Player: gg"));
        assertTrue(ChatTabs.matches(1, "[Party] Player: gg"));
        assertFalse(ChatTabs.matches(1, "Guild > Player: gg"));

        // GUILD
        assertTrue(ChatTabs.matches(2, "Guild > Player: gg"));
        assertTrue(ChatTabs.matches(2, "[Guild] Player: gg"));
        assertFalse(ChatTabs.matches(2, "Party > Player: gg"));

        // PMS
        assertTrue(ChatTabs.matches(3, "From Player: hello"));
        assertTrue(ChatTabs.matches(3, "To Player: hello"));
        assertTrue(ChatTabs.matches(3, "[PM] Player: hi"));
        assertFalse(ChatTabs.matches(3, "Public chat message"));
    }

    @Test
    public void testCustomTab() {
        ChatTabs.addTab("AUCTION", "(?:Auction|AH >)");
        int customIdx = ChatTabs.getTabCount() - 1;
        assertTrue(ChatTabs.matches(customIdx, "AH > You bought an item!"));
        assertFalse(ChatTabs.matches(customIdx, "Random text"));
    }
}
