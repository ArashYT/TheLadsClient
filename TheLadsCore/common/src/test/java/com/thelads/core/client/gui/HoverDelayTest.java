package com.thelads.core.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HoverDelayTest {
    private static final long MS = 1_000_000L;

    @Test void showsAfterRestingOnOneCardAndRestartsOnAnotherOrAfterDismiss() {
        var tip = new LadsSettingsScreen.HoverDelay(750 * MS);
        assertFalse(tip.update("FPS", 0)); assertFalse(tip.update("FPS", 749 * MS)); assertTrue(tip.update("FPS", 750 * MS));
        assertFalse(tip.update("Zoom", 800 * MS), "moving to another card restarts the delay");
        assertTrue(tip.update("Zoom", 1550 * MS));
        tip.dismiss(); assertFalse(tip.update("Zoom", 5000 * MS), "a click or scroll hides it while the pointer stays");
        assertFalse(tip.update(null, 5001 * MS)); assertFalse(tip.update("Zoom", 5002 * MS));
        assertTrue(tip.update("Zoom", 5752 * MS), "leaving and coming back starts over");
    }
}
