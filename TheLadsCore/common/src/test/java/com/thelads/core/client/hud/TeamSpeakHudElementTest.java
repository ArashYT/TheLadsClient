package com.thelads.core.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeamSpeakHudElementTest {
    @Test
    void testDimensionsAndDefaults() {
        TeamSpeakHudElement hud = new TeamSpeakHudElement();
        assertEquals(110, hud.width);
        assertEquals(14, hud.height);
        assertTrue(hud.enabled);
    }
}
