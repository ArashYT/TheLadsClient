package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StreamerModeTest {
    @Test
    void scoreboardMaskingPureStringFunction() {
        // Date line + Server ID
        assertTrue(HypixelChat.isStreamerScoreboardLine("10/20/26 L32C"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("10/20/26 mini123A"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("05/14/24 m23B"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("§710/20/26 §8L32C"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("§710/21/26 §8m149AM"));

        // Date only
        assertTrue(HypixelChat.isStreamerScoreboardLine("10/20/26"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("§710/20/26"));

        // Standalone Server / Lobby ID
        assertTrue(HypixelChat.isStreamerScoreboardLine("L32C"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("mini123A"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("mini149AM"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("mega12B"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("Lobby 1"));
        assertTrue(HypixelChat.isStreamerScoreboardLine("Lobby #12"));

        // Normal minigame content lines that MUST NOT be masked
        assertFalse(HypixelChat.isStreamerScoreboardLine("Bed Wars"));
        assertFalse(HypixelChat.isStreamerScoreboardLine("Kills: 5"));
        assertFalse(HypixelChat.isStreamerScoreboardLine("Final Kills: 2"));
        assertFalse(HypixelChat.isStreamerScoreboardLine("Beds Broken: 1"));
        assertFalse(HypixelChat.isStreamerScoreboardLine("Red: 4"));
        assertFalse(HypixelChat.isStreamerScoreboardLine("Blue: 3"));
        assertFalse(HypixelChat.isStreamerScoreboardLine("www.hypixel.net"));
        assertFalse(HypixelChat.isStreamerScoreboardLine(""));
        assertFalse(HypixelChat.isStreamerScoreboardLine(null));
    }

    @Test
    void chatLobbyMaskingPureStringFunction() {
        assertEquals("Sending you to [HIDDEN]!", HypixelChat.maskStreamerChat("Sending you to mini123A!"));
        assertEquals("Sending you to [HIDDEN]...", HypixelChat.maskStreamerChat("Sending you to mini149AM..."));
        assertEquals("Sending you to [HIDDEN]", HypixelChat.maskStreamerChat("Sending you to L32C"));
        assertEquals("§aSending you to §e[HIDDEN]§a!", HypixelChat.maskStreamerChat("§aSending you to §emini123A§a!"));

        // Other chat lines untouched
        assertEquals("The game starts in 1 second!", HypixelChat.maskStreamerChat("The game starts in 1 second!"));
        assertEquals("[MVP+] Player: gg", HypixelChat.maskStreamerChat("[MVP+] Player: gg"));
        assertNull(HypixelChat.maskStreamerChat(null));
    }
}
