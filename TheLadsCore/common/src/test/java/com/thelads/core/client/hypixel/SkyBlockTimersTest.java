package com.thelads.core.client.hypixel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyBlockTimersTest {

    @BeforeEach
    @AfterEach
    void resetTimers() {
        SkyBlockTimers.clearTimers();
    }

    @Test
    void testComposterLifecycle() {
        assertEquals("Composter: 4m 12s", SkyBlockTimers.getComposterString(true));

        assertEquals("Composter: Ready", SkyBlockTimers.getComposterString(false));

        SkyBlockTimers.setComposterRemainingSeconds(120);
        String s = SkyBlockTimers.getComposterString(false);
        assertTrue(s.contains("Composter: 1m") || s.contains("Composter: 2m"), "Expected ~2m remaining");

        SkyBlockTimers.setComposterRemainingSeconds(0);
        assertEquals("Composter: Ready", SkyBlockTimers.getComposterString(false));
    }

    @Test
    void testPestLifecycle() {
        assertEquals("Pest: 2m 45s", SkyBlockTimers.getPestString(true));

        assertEquals("Pest: Ready", SkyBlockTimers.getPestString(false));

        SkyBlockTimers.resetPestTimer();
        String s = SkyBlockTimers.getPestString(false);
        assertTrue(s.contains("Pest: 9m") || s.contains("Pest: 10m"), "Expected ~10m remaining on reset");
    }

    @Test
    void testChatTriggers() {
        // Hypixel SkyBlock Garden pest spawn announcement
        SkyBlockTimers.handleChatMessage("GROSS! A Pest has appeared in Plot - 4! CLICK HERE to teleport to the plot!");
        String pest = SkyBlockTimers.getPestString(false);
        assertTrue(pest.contains("Pest: 9m") || pest.contains("Pest: 10m"));
    }

    @Test
    void testCachedStrings() {
        SkyBlockTimers.setComposterRemainingSeconds(100);
        String comp1 = SkyBlockTimers.getComposterString(false);
        String comp2 = SkyBlockTimers.getComposterString(false);
        assertSame(comp1, comp2, "Repeated calls within same second should return identical cached string reference");

        SkyBlockTimers.resetPestTimer();
        String pest1 = SkyBlockTimers.getPestString(false);
        String pest2 = SkyBlockTimers.getPestString(false);
        assertSame(pest1, pest2, "Repeated calls within same second should return identical cached string reference");
    }
}
