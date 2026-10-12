package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NametagDepthTest {
    @Test
    void textGoesTowardsTheCamera() {
        // A tag 10 blocks ahead (camera space looks down -z), its z axis scaled 0.025: pointing away (1.20.1) or at the camera (26.x).
        assertEquals(-NametagDepth.VANILLA, NametagDepth.textOffset(0, 0, -0.025F, 0, 0, -10), 1e-6);
        assertEquals(NametagDepth.VANILLA, NametagDepth.textOffset(0, 0, 0.025F, 0, 0, -10), 1e-6);
    }

    @Test
    void gapHoldsFourDepthStepsFarAway() {
        float local = NametagDepth.textOffset(0, 0, 0.025F, 0, 0, -64);
        float blocks = local * 0.025F, step = 64F * 64F / 840_000F;
        assertTrue(blocks >= 4 * step && blocks < 0.03F, "gap " + blocks + " blocks, depth step " + step);
    }

    @Test
    void noScaleKeepsVanilla() {
        assertEquals(NametagDepth.VANILLA, NametagDepth.textOffset(0, 0, 0, 1, 2, 3), 0);
    }
}
