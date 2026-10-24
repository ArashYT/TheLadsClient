package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkAnimatorTest {
    @Test
    void startsBelowAndArrives() {
        assertEquals(-ChunkAnimator.TRAVEL, ChunkAnimator.offset(false, 1000, 0), 1e-4);
        assertEquals(0F, ChunkAnimator.offset(false, 1000, 1_000_000_000L), 0);
        assertEquals(0F, ChunkAnimator.offset(false, 1000, 5_000_000_000L), 0);
    }

    @Test
    void easesOut() {
        float half = ChunkAnimator.offset(false, 1000, 500_000_000L);
        assertEquals(-ChunkAnimator.TRAVEL / 8, half, 1e-4); // (1 - 0.5)^3 of the way left
        assertTrue(Math.abs(ChunkAnimator.offset(false, 1000, 100_000_000L)) > Math.abs(half), "moves fastest first");
    }

    @Test
    void fromAboveDrops() {
        assertEquals(ChunkAnimator.TRAVEL, ChunkAnimator.offset(true, 1000, 0), 1e-4);
    }

    @Test
    void longerDurationTakesLonger() {
        assertTrue(Math.abs(ChunkAnimator.offset(false, 3000, 1_000_000_000L)) > 0);
    }
}
