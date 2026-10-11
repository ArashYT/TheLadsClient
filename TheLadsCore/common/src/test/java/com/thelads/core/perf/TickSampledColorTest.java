package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TickSampledColorTest {
    /** A smooth field like the gaussian-blended biome colours (quart units: they change over several units). */
    private static final TickSampledColor.Sampler FIELD = (x, y, z, rgb) -> {
        rgb[0] = 0.5 + 0.4 * Math.sin(x * 0.3);
        rgb[1] = 0.5 + 0.4 * Math.cos(y * 0.2 + z * 0.1);
        rgb[2] = 0.2 + 0.01 * x;
    };

    /** Flying (an elytra's ~0.7 quart a tick): every frame between ticks matches a fresh sample closely, one sample per tick. */
    @Test
    void framesOnThePathInterpolateWithOneSamplePerTick() {
        TickSampledColor cache = new TickSampledColor();
        double[] out = new double[3], exact = new double[3];
        double x = 0, step = 0.7;
        for (int tick = 0; tick < 40; tick++, x += step) {
            for (double pt : new double[] {0, 0.2, 0.5, 0.8, 1}) {
                double px = x + pt * step;
                assertTrue(cache.get(tick, x, 10, 3, x + step, 10, 3, px, 10, 3, FIELD, out));
                FIELD.sample(px, 10, 3, exact);
                for (int i = 0; i < 3; i++) assertEquals(exact[i], out[i], 0.01, "tick " + tick + " pt " + pt); // < 3/255
            }
        }
        assertEquals(41, cache.samples); // A and B on the first tick, then only the new B
    }

    @Test
    void endsAreExactAndALinearFieldIsExactEverywhere() {
        TickSampledColor cache = new TickSampledColor();
        double[] out = new double[3];
        TickSampledColor.Sampler linear = (x, y, z, rgb) -> { rgb[0] = x; rgb[1] = 2 * y; rgb[2] = x + z; };
        assertTrue(cache.get(1, 0, 0, 0, 4, 2, 0, 1, 0.5, 0, linear, out));
        assertArrayEquals(new double[] {1, 1, 1}, out, 1e-12);
        assertTrue(cache.get(1, 0, 0, 0, 4, 2, 0, 4, 2, 0, linear, out));
        assertArrayEquals(new double[] {4, 4, 4}, out, 1e-12);
    }

    /** Third person or another mod's position: left to the caller, and nothing is sampled for it. */
    @Test
    void offThePathIsLeftToTheCaller() {
        TickSampledColor cache = new TickSampledColor();
        double[] out = {7, 7, 7};
        assertFalse(cache.get(1, 0, 0, 0, 1, 0, 0, 0.5, 1, 0, FIELD, out));
        assertFalse(cache.get(1, 0, 0, 0, 1, 0, 0, 2, 0, 0, FIELD, out)); // beyond B
        assertArrayEquals(new double[] {7, 7, 7}, out);
        assertEquals(0, cache.samples);
    }

    /** Standing still the colour is still refreshed every tick (the fog colour follows the time of day); a teleport samples both ends. */
    @Test
    void refreshesEachTickAndAfterATeleport() {
        TickSampledColor cache = new TickSampledColor();
        double[] out = new double[3];
        for (int tick = 0; tick < 5; tick++) {
            for (int frame = 0; frame < 3; frame++) assertTrue(cache.get(tick, 2, 2, 2, 2, 2, 2, 2, 2, 2, FIELD, out));
        }
        assertEquals(6, cache.samples);
        assertTrue(cache.get(5, 100, 2, 2, 100, 2, 2, 100, 2, 2, FIELD, out));
        assertEquals(8, cache.samples);
    }
}
