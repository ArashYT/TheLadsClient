package com.thelads.core.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** PARITY-1023 pvp rows 31 and 37: the oof's distance falloff and the despawn blink. */
class PvpModulesTest {
    @Test void despawnBlinkSpeedsUpNearTheEnd() {
        int warn = 600, life = 6000;
        assertFalse(ItemDespawnModule.hidden(life - 601, life, warn), "before the warning");
        int off = 0;
        for (int age = life - 600; age < life - 200; age++) if (ItemDespawnModule.hidden(age, life, warn)) off++;
        assertEquals(200, off, "half of the slow blink is off");
        boolean last = ItemDespawnModule.hidden(life - 59, life, warn);
        int flips = 0;
        for (int age = life - 59; age < life; age++) {
            boolean now = ItemDespawnModule.hidden(age, life, warn);
            if (now != last) flips++;
            last = now;
        }
        assertTrue(flips >= 25, "the last 3 seconds flip every 2 ticks, " + flips);
        assertFalse(ItemDespawnModule.hidden(-32768, life, warn), "an item that never despawns never blinks");
        assertFalse(ItemDespawnModule.hidden(life + 5, life, warn), "past despawn (lag): drawn");
    }

    @Test void oofFadesOverSixteenBlocks() {
        assertEquals(1, OofModule.falloff(0), 1e-9);
        assertEquals(0.5, OofModule.falloff(8), 1e-9);
        assertEquals(0, OofModule.falloff(40), 1e-9);
    }
}
