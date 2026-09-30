package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native animation invariants: frame-rate independence, bounded travel, toggles and pause reset. */
class FrameAnimationTest {
    @Test void sameElapsedTimeGivesSameBoundedTravelAtEveryFrameRate() {
        double expected = 160 * (1 - Math.exp(-18 * .25));
        for (int frames : new int[] { 8, 15, 30, 36, 60, 120 }) {
            FrameAnimation animation = new FrameAnimation();
            assertEquals(0, animation.update(0, 18, 1_000_000_000L, true), "initial position");
            double position = 0;
            for (int frame = 1; frame <= frames; frame++) {
                double next = animation.update(160, 18, 1_000_000_000L + 250_000_000L * frame / frames, true);
                assertTrue(next >= position && next <= 160, "no overshoot at " + frames + " frames");
                position = next;
            }
            assertEquals(expected, position, .00001, "same elapsed time gives same travel at " + frames + " frames");
        }
    }
    @Test void togglesPausesAndClockJumps() {
        FrameAnimation animation = new FrameAnimation();
        animation.update(100, 18, 1_000_000_000L, true);
        assertEquals(0, animation.update(0, 18, 1_016_000_000L, false), "disable takes immediate effect");
        double moving = animation.update(100, 18, 1_032_000_000L, true);
        assertTrue(moving > 0 && moving < 100, "re-enabled resumes smoothly");
        assertEquals(50, animation.update(50, 18, 2_000_000_000L, true), "long pause snaps to current selection");
        assertEquals(70, animation.update(70, 18, 1_000_000_000L, true), "clock discontinuity resets");
        assertEquals(70, animation.update(0, 18, 1_000_000_000L, true), "duplicate frame timestamp does not move");
    }
}
