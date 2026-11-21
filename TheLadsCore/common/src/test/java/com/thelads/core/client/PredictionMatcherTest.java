package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PredictionMatcherTest {
    @Test void suppressesTheServerCopyOnceInsideWindowAndRadius() {
        PredictionMatcher m = new PredictionMatcher(500, 2);
        m.record("entity.player.attack.strong", 10, 64, 10, 1000);
        assertTrue(m.shouldSuppress("entity.player.attack.strong", 10.5, 64, 10.5, 1120));
        assertFalse(m.shouldSuppress("entity.player.attack.strong", 10.5, 64, 10.5, 1130), "used up");
    }

    @Test void nothingIsSuppressedAfterTheWindowOrFarAway() {
        PredictionMatcher m = new PredictionMatcher(500, 2);
        m.record("entity.player.attack.strong", 0, 0, 0, 1000);
        assertFalse(m.shouldSuppress("entity.player.attack.strong", 0, 0, 0, 1501), "expired");
        m.record("entity.player.attack.strong", 0, 0, 0, 2000);
        assertFalse(m.shouldSuppress("entity.player.attack.strong", 5, 0, 0, 2100), "another player's hit");
        assertTrue(m.shouldSuppress("entity.player.attack.strong", 1, 0, 0, 2200), "the real one is still waiting");
    }

    @Test void oneRecordAbsorbsOnePacket() {
        PredictionMatcher m = new PredictionMatcher();
        m.record("a.b", 0, 0, 0, 0);
        m.record("a.b", 0, 0, 0, 10);
        assertTrue(m.shouldSuppress("a.b", 0, 0, 0, 100));
        assertTrue(m.shouldSuppress("a.b", 0, 0, 0, 100));
        assertFalse(m.shouldSuppress("a.b", 0, 0, 0, 100));
    }

    @Test void exactIdBeatsFamilyAndFamilyCoversAMispredictedVariant() {
        PredictionMatcher m = new PredictionMatcher();
        m.record("entity.player.attack.knockback", 0, 0, 0, 0);
        m.record("entity.player.attack.strong", 0, 0, 0, 0);
        assertTrue(m.shouldSuppress("entity.player.attack.strong", 0, 0, 0, 50));
        assertTrue(m.shouldSuppress("entity.player.attack.weak", 0, 0, 0, 50), "family fallback takes the knockback entry");
        assertFalse(m.shouldSuppress("entity.player.attack.weak", 0, 0, 0, 50));
        assertFalse(m.shouldSuppress("block.stone.hit", 0, 0, 0, 50), "other families are never touched");
    }

    @Test void clearAndWrapAround() {
        PredictionMatcher m = new PredictionMatcher();
        for (int i = 0; i < 40; i++) m.record("x.y", 0, 0, 0, i);
        assertTrue(m.shouldSuppress("x.y", 0, 0, 0, 40));
        m.clear();
        assertFalse(m.shouldSuppress("x.y", 0, 0, 0, 41));
    }
}
