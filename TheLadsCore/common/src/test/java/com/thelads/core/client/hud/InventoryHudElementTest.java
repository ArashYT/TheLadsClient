package com.thelads.core.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryHudElementTest {
    @Test
    void testDimensionsAndDefaults() {
        InventoryHudElement hud = new InventoryHudElement();
        assertEquals(162, hud.width);
        assertEquals(54, hud.height);
        assertTrue(hud.enabled);
    }
}
