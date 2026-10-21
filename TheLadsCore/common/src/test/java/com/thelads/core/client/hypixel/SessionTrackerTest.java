package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionTrackerTest {
    private final List<String> chatMessages = new ArrayList<>();
    private final LadsGameBridge bridge = new DefaultGameBridge() {
        @Override
        public void displayChatMessage(String text) {
            chatMessages.add(text);
        }
    };

    @BeforeEach
    void setUp() {
        SessionTracker.reset();
        chatMessages.clear();
        LadsGameBridge.set(bridge);
    }

    @AfterEach
    void tearDown() {
        SessionTracker.reset();
        LadsGameBridge.set(new DefaultGameBridge());
    }

    @Test
    void initialCountersAreZero() {
        assertEquals(0, SessionTracker.getGames());
        assertEquals(0, SessionTracker.getWins());
        assertEquals(0, SessionTracker.getLosses());
        assertEquals(0, SessionTracker.getKills());
        assertEquals(0, SessionTracker.getFinalKills());
        assertEquals(0, SessionTracker.getDeaths());
        assertEquals(0, SessionTracker.getBedsBroken());
        assertEquals(0, SessionTracker.getCurrentStreak());
        assertEquals(0, SessionTracker.getBestStreak());
        assertEquals(0.0, SessionTracker.getWinLossRatio());
        assertEquals(0.0, SessionTracker.getKillDeathRatio());
    }

    @Test
    void killsAndDeathsFromChat() {
        String me = "Arash_YT";

        // Regular kill
        SessionTracker.onServerChat("Enemy was killed by Arash_YT.", me);
        assertEquals(1, SessionTracker.getKills());
        assertEquals(0, SessionTracker.getFinalKills());

        // Cosmetic kill line
        SessionTracker.onServerChat("Enemy took the L to Arash_YT.", me);
        assertEquals(2, SessionTracker.getKills());

        // Void kill
        SessionTracker.onServerChat("[RED] Enemy was knocked into the void by [BLUE] Arash_YT.", me);
        assertEquals(3, SessionTracker.getKills());
        assertEquals(0, SessionTracker.getFinalKills());

        // Final kill
        SessionTracker.onServerChat("[RED] Enemy was knocked into the void by [BLUE] Arash_YT. FINAL KILL!", me);
        assertEquals(4, SessionTracker.getKills());
        assertEquals(1, SessionTracker.getFinalKills());

        // Other players' kills should not count towards our stats
        SessionTracker.onServerChat("Bob was killed by Alice.", me);
        assertEquals(4, SessionTracker.getKills());
        assertEquals(1, SessionTracker.getFinalKills());

        // Our deaths
        SessionTracker.onServerChat("Arash_YT was killed by Enemy.", me);
        assertEquals(1, SessionTracker.getDeaths());

        SessionTracker.onServerChat("Arash_YT fell into the void.", me);
        assertEquals(2, SessionTracker.getDeaths());

        // Player chat fake messages should be ignored
        SessionTracker.onServerChat("[MVP+] Steve: Enemy was killed by Arash_YT.", me);
        assertEquals(4, SessionTracker.getKills());
    }

    @Test
    void bedDestructionFromChat() {
        String me = "Arash_YT";

        SessionTracker.onServerChat("BED DESTRUCTION > Red Bed was destroyed by Arash_YT!", me);
        assertEquals(1, SessionTracker.getBedsBroken());

        SessionTracker.onServerChat("BED DESTRUCTION > Blue Bed was destroyed by [MVP+] Arash_YT!", me);
        assertEquals(2, SessionTracker.getBedsBroken());

        // Other player breaking bed should not count for us
        SessionTracker.onServerChat("BED DESTRUCTION > Green Bed was destroyed by [VIP] Teammate!", me);
        assertEquals(2, SessionTracker.getBedsBroken());
    }

    @Test
    void winLossAndStreaks() {
        String me = "Arash_YT";

        // Game 1: Victory!
        SessionTracker.onServerChat("The game starts in 1 second!", me);
        assertTrue(SessionTracker.isGameInProgress());
        SessionTracker.onServerChat("Enemy was killed by Arash_YT. FINAL KILL!", me);
        SessionTracker.onServerChat("                VICTORY!", me);
        SessionTracker.onServerChat("   Winner - BLUE", me);

        assertEquals(1, SessionTracker.getGames());
        assertEquals(1, SessionTracker.getWins());
        assertEquals(0, SessionTracker.getLosses());
        assertEquals(1, SessionTracker.getCurrentStreak());
        assertEquals(1, SessionTracker.getBestStreak());
        assertEquals(1.0, SessionTracker.getWinLossRatio());

        // Game 2: Victory!
        SessionTracker.onServerChat("The game starts in 1 second!", me);
        SessionTracker.onServerChat("                VICTORY!", me);
        assertEquals(2, SessionTracker.getGames());
        assertEquals(2, SessionTracker.getWins());
        assertEquals(0, SessionTracker.getLosses());
        assertEquals(2, SessionTracker.getCurrentStreak());
        assertEquals(2, SessionTracker.getBestStreak());

        // Game 3: Defeat!
        SessionTracker.onServerChat("The game starts in 1 second!", me);
        SessionTracker.onServerChat("Arash_YT was killed by Enemy. FINAL KILL!", me);
        SessionTracker.onServerChat("   Winner - RED", me);
        SessionTracker.onServerChat("   1st Place - RED", me);

        assertEquals(3, SessionTracker.getGames());
        assertEquals(2, SessionTracker.getWins());
        assertEquals(1, SessionTracker.getLosses());
        assertEquals(0, SessionTracker.getCurrentStreak());
        assertEquals(2, SessionTracker.getBestStreak());
        assertEquals(2.0, SessionTracker.getWinLossRatio());
    }

    @Test
    void duelWinnerLineCountsAsVictory() {
        String me = "Arash_YT";
        SessionTracker.onServerChat("The game starts in 1 second!", me);
        SessionTracker.onServerChat("         [MVP+] Enemy   [MVP+] Arash_YT WINNER!", me);

        assertEquals(1, SessionTracker.getGames());
        assertEquals(1, SessionTracker.getWins());
        assertEquals(0, SessionTracker.getLosses());
        assertEquals(1, SessionTracker.getCurrentStreak());
    }

    @Test
    void ratiosCalculation() {
        SessionTracker.onKill(false);
        SessionTracker.onKill(false);
        SessionTracker.onKill(true); // 3 kills
        SessionTracker.onDeath();
        SessionTracker.onDeath();    // 2 deaths

        assertEquals(1.5, SessionTracker.getKillDeathRatio());
    }

    @Test
    void sessionCommandPrintsSummaryAndReset() {
        SessionTracker.onKill(true);
        SessionTracker.onDeath();

        // Trigger /session command
        assertTrue(HypixelMods.handleOutgoingCommand("/session"));
        assertFalse(chatMessages.isEmpty());
        assertTrue(chatMessages.stream().anyMatch(msg -> msg.contains("Session Statistics")));

        // Trigger /session reset
        chatMessages.clear();
        assertTrue(HypixelMods.handleOutgoingCommand("/session reset"));
        assertEquals(0, SessionTracker.getKills());
        assertEquals(0, SessionTracker.getDeaths());
        assertTrue(chatMessages.stream().anyMatch(msg -> msg.contains("reset")));
    }
}
