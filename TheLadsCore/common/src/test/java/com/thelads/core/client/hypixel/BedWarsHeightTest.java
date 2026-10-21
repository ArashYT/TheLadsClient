package com.thelads.core.client.hypixel;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BedWarsHeightTest {
    @BeforeEach
    void setUp() {
        BedWarsHeight.resetForTest();
    }

    @AfterEach
    void tearDown() {
        BedWarsHeight.resetForTest();
    }

    @Test
    void knownMapHeights() {
        assertEquals(111, BedWarsHeight.getLimit("Lighthouse"));
        assertEquals(91, BedWarsHeight.getLimit("Speedway"));
        assertEquals(101, BedWarsHeight.getLimit("Airshow"));
        assertEquals(94, BedWarsHeight.getLimit("Amazon"));
        assertEquals(99, BedWarsHeight.getLimit("Apollo"));
        assertEquals(111, BedWarsHeight.getLimit("Aquarium"));
        assertEquals(106, BedWarsHeight.getLimit("Ashfire"));
        assertEquals(105, BedWarsHeight.getLimit("Orchestra"));
        assertEquals(113, BedWarsHeight.getLimit("Pernicious"));
        assertEquals(108, BedWarsHeight.getLimit("Rooftop"));
        assertEquals(100, BedWarsHeight.getLimit("Zarzul"));
    }

    @Test
    void defaultFallback() {
        assertEquals(BedWarsHeight.DEFAULT_LIMIT, BedWarsHeight.getLimit("UnknownMap123"));
        assertEquals(BedWarsHeight.DEFAULT_LIMIT, BedWarsHeight.getLimit(null));
        assertEquals(BedWarsHeight.DEFAULT_LIMIT, BedWarsHeight.getLimit(""));
    }

    @Test
    void runtimeHeightLimitLearning() {
        // Chat message variations
        assertTrue(BedWarsHeight.isLimitReachedMessage("Build height limit reached!"));
        assertTrue(BedWarsHeight.isLimitReachedMessage("Build height limit reached"));
        assertTrue(BedWarsHeight.isLimitReachedMessage("You have reached the build height limit!"));
        assertTrue(BedWarsHeight.isLimitReachedMessage("§cBuild height limit reached!"));
        assertFalse(BedWarsHeight.isLimitReachedMessage("You placed a block."));

        // Learn height for an unknown map
        assertEquals(110, BedWarsHeight.getLimit("MyCustomMap"));
        assertTrue(BedWarsHeight.learnLimit("MyCustomMap", 95));
        assertEquals(95, BedWarsHeight.getLimit("MyCustomMap"));
        assertEquals(95, BedWarsHeight.getLimit("mycustommap")); // Case insensitive

        // Learned limit overrides static limit (e.g. if Hypixel updates a map)
        assertEquals(111, BedWarsHeight.getLimit("Lighthouse"));
        BedWarsHeight.learnLimit("Lighthouse", 115);
        assertEquals(115, BedWarsHeight.getLimit("Lighthouse"));
    }
}
