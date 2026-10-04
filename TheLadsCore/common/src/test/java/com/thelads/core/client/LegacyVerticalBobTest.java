package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegacyVerticalBobTest {
    private static final int FALL = 12, FLY = 12;

    /** Ticks as the game runs them (one sample per tick), frames read in between: a fall, then flying from tick FALL on. */
    private static float[] frames(int fps) {
        var bob = new LegacyVerticalBob();
        int frames = (FALL + FLY) * fps / 20;
        float[] out = new float[frames];
        int tick = -1;
        for (int frame = 0; frame < frames; frame++) {
            double time = frame / (double) fps * 20;
            while (tick < (int) time) {
                tick++;
                boolean flying = tick >= FALL;
                bob.sample(tick, flying ? 0 : -3, false, !flying, 1, true);
            }
            out[frame] = bob.interpolate((float) (time - tick));
        }
        return out;
    }

    @Test void flyingAfterAFallEasesThePitchBackSmoothlyAtEveryFrameRate() {
        float[] reference = frames(20);
        float tilted = reference[FALL - 1];
        assertTrue(tilted > 5, "falling tilts the camera (" + tilted + ")");
        for (int fps : new int[]{30, 60, 120, 144, 240}) {
            float[] f = frames(fps);
            int start = FALL * fps / 20;
            float biggest = 0;
            for (int i = start; i < f.length; i++) {
                biggest = Math.max(biggest, Math.abs(f[i] - f[i - 1]));
                assertTrue(f[i] <= f[i - 1] + 1e-6f && f[i] >= 0, fps + " fps: the pitch only falls back to level, frame " + i);
            }
            // At most the settle share of the tilt per tick, spread over the tick's frames: no snap.
            assertTrue(biggest <= tilted * LegacyVerticalBob.SETTLE * 20 / fps + 1e-4f, fps + " fps: largest step " + biggest);
            assertTrue(f[f.length - 1] < tilted * 0.01f, fps + " fps: level again within " + FLY + " ticks (" + f[f.length - 1] + ")");
            // Frame-rate independent: whole ticks read the same pitch at every frame rate.
            for (int tick = 0; tick < FALL + FLY; tick++)
                if (tick * fps % 20 == 0) assertEquals(reference[tick], f[tick * fps / 20], 1e-5f, fps + " fps, tick " + tick);
        }
    }

    @Test void landingKeeps18sQuickerEase() {
        var bob = new LegacyVerticalBob();
        float fall = bob.sample(0, -1.5, false, true, 1, true);
        assertEquals((float) (Math.atan(0.3) * 15 * LegacyVerticalBob.FOLLOW), fall, 1e-5f, "1.8's falling pitch");
        assertEquals(fall * (1 - LegacyVerticalBob.FOLLOW), bob.sample(1, 0, true, true, 1, true), 1e-5f, "landing closes 80% a tick");
        assertEquals(0, bob.sample(2, -1.5, false, true, 1, false), "the module off resets at once");
    }
}
