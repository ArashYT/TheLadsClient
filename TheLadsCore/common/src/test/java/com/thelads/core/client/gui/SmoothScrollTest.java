package com.thelads.core.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmoothScrollTest {

    @Test
    void stepNoMovementWhenAtTarget() {
        assertEquals(100.0, SmoothScroll.step(100.0, 100.0, 1.0 / 60.0));
    }

    @Test
    void stepMonotonicConvergenceDown() {
        double current = 0.0;
        double target = 100.0;
        double dt = 1.0 / 60.0;
        for (int frame = 0; frame < 60; frame++) {
            double next = SmoothScroll.step(current, target, dt);
            assertTrue(next > current, "Each step must strictly advance toward target");
            assertTrue(next <= target, "Step must not overshoot target");
            current = next;
            if (current == target) break;
        }
        assertEquals(target, current, "Must converge to target");
    }

    @Test
    void stepMonotonicConvergenceUp() {
        double current = 100.0;
        double target = 0.0;
        double dt = 1.0 / 60.0;
        for (int frame = 0; frame < 60; frame++) {
            double next = SmoothScroll.step(current, target, dt);
            assertTrue(next < current, "Each step must strictly advance toward target");
            assertTrue(next >= target, "Step must not overshoot target");
            current = next;
            if (current == target) break;
        }
        assertEquals(target, current, "Must converge to target");
    }

    @Test
    void stepSnapsWithinThreshold() {
        assertEquals(10.0, SmoothScroll.step(9.85, 10.0, 1.0 / 60.0));
        assertEquals(10.0, SmoothScroll.step(10.15, 10.0, 1.0 / 60.0));
    }

    @Test
    void stepEasingDurationApproxPointThreeFiveSeconds() {
        // Typical scroll distance (e.g. 60-100 px across multiple lines or notches)
        double current = 0.0;
        double target = 60.0;
        double dt = 1.0 / 60.0;
        int frames = 0;
        while (current < target && frames < 100) {
            current = SmoothScroll.step(current, target, dt);
            frames++;
        }
        // At 18.0 decay rate: ~20 frames is 0.33 s
        assertTrue(frames >= 18 && frames <= 24, "Easing duration should be ~0.35 s (18-24 frames at 60 fps), took " + frames + " frames");
    }
}
