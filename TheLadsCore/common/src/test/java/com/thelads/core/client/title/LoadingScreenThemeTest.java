package com.thelads.core.client.title;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The loading ring is a pure function of the clock: a Windows-style chase that repeats and never jumps. */
class LoadingScreenThemeTest {
    @Test void firstDotStartsAtTheBottomAndOthersFollowInOrder() {
        double[] start = LoadingScreenTheme.dotAngles(0);
        assertEquals(180, start[0], 1e-9, "dot 0 enters at 6 o'clock");
        for (int i = 1; i < LoadingScreenTheme.DOTS; i++) assertTrue(Double.isNaN(start[i]), "dot " + i + " has not entered yet");
        double[] later = LoadingScreenTheme.dotAngles(1.2);
        for (double angle : later) assertTrue(angle >= 0 && angle < 360, "every dot is on the ring");
        assertNotEquals(later[0], LoadingScreenTheme.dotAngles(1.3)[0], "the ring moves with time");
    }

    @Test void repeatsEveryCycleAndHidesBetweenLaps() {
        for (double t = 0; t < 6; t += .37)
            assertArrayEquals(LoadingScreenTheme.dotAngles(t), LoadingScreenTheme.dotAngles(t + 5.5), 1e-9, "periodic at " + t);
        assertTrue(Double.isNaN(LoadingScreenTheme.dotAngle(.8)), "hidden after two laps");
        assertEquals(180, LoadingScreenTheme.dotAngle(.7499999), .01, "ends back at the bottom");
    }

    @Test void movesForwardSmoothlyFastAtTheBottomSlowAtTheTop() {
        double previous = LoadingScreenTheme.dotAngle(0), travelled = 0, slowest = 360, fastest = 0;
        for (double t = .001; t < .75; t += .001) {
            double angle = LoadingScreenTheme.dotAngle(t), step = (angle - previous + 360) % 360;
            assertTrue(step < 15, "no jump at " + t);
            travelled += step;
            if (angle > 330 || angle < 30) slowest = Math.min(slowest, step);
            else if (angle > 150 && angle < 210) fastest = Math.max(fastest, step);
            previous = angle;
        }
        assertEquals(720, travelled, 1, "two full laps per cycle");
        assertTrue(fastest > 3 * slowest, "eases: fast through the bottom, slow over the top");
    }
}
