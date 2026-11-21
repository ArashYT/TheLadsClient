package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkPriorityTest {
    @Test
    void frontBeforeSideBeforeBackAtEqualDistance() {
        float yaw = 0f; // looking +Z
        double front = ChunkPriority.score(0, 8, yaw);
        double side = ChunkPriority.score(8, 0, yaw);
        double back = ChunkPriority.score(0, -8, yaw);
        assertTrue(front < side, "front < side");
        assertTrue(side <= back, "side <= back");
        assertTrue(front < back);
    }

    @Test
    void yawTurnsTheBias() {
        // yaw 90 looks toward -X
        assertTrue(ChunkPriority.score(-8, 0, 90f) < ChunkPriority.score(8, 0, 90f));
    }

    @Test
    void nearestRingKeepsVanillaOrderAndIsNeverOutranked() {
        float yaw = 37f;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                // farthest-possible near chunk vs the best-placed far chunk straight ahead
                assertTrue(ChunkPriority.score(dx, dz, yaw) < ChunkPriority.score(0, 3, 0f));
            }
        }
        assertTrue(ChunkPriority.score(1, 0, 123f) < ChunkPriority.score(2, 2, 123f));
        assertEquals(ChunkPriority.score(1, 0, 10f), ChunkPriority.score(0, 1, 200f));
    }

    @Test
    void meshDistanceBiasesFrontAndSparesTheNearSections() {
        double d = 100.0 * 100.0;
        assertTrue(ChunkPriority.meshDistance(d, 0, 100, 0f) < ChunkPriority.meshDistance(d, 0, -100, 0f));
        assertEquals(d, ChunkPriority.meshDistance(d, 0, -100, 0f));
        assertEquals(3200.0, ChunkPriority.meshDistance(3200.0, 0, 56.5, 0f));
        for (double dist = 3301; dist < 40000; dist += 97) {
            assertTrue(ChunkPriority.meshDistance(dist, 0, Math.sqrt(dist), 0f) >= ChunkPriority.NEAR_DIST_SQ);
        }
    }

    @Test
    void keyKeepsOrdering() {
        assertTrue(ChunkPriority.key(0, 8, 0f) < ChunkPriority.key(0, -8, 0f));
        assertTrue(ChunkPriority.key(1, 1, 0f) < ChunkPriority.key(0, 3, 0f));
    }
}
