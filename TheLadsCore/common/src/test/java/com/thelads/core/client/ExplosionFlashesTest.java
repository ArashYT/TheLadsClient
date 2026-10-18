package com.thelads.core.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExplosionFlashesTest {
    private ExplosionFlashes flashes;

    @BeforeEach
    void setUp() {
        flashes = new ExplosionFlashes();
    }

    @Test
    void flashStartsAtFullBrightnessAndFadesOverHalfSecond() {
        // Standard TNT explosion (power 4.0) -> 10 ticks = 0.5s
        flashes.add(10.0, 64.0, 10.0, 4.0f);
        assertEquals(1, flashes.count());
        assertEquals(10, flashes.duration(0));
        assertEquals(0, flashes.age(0));
        assertEquals(15, flashes.luminance(0), "starts at full brightness 15");

        // After 5 ticks (0.25s, halfway)
        for (int i = 0; i < 5; i++) flashes.tick();
        assertEquals(1, flashes.count());
        assertEquals(5, flashes.age(0));
        int midLum = flashes.luminance(0);
        assertTrue(midLum >= 7 && midLum <= 8, "fades evenly halfway: expected 7-8, got " + midLum);

        // After 9 ticks (near end)
        for (int i = 0; i < 4; i++) flashes.tick();
        assertEquals(1, flashes.count());
        assertEquals(9, flashes.age(0));
        assertTrue(flashes.luminance(0) <= 2, "dim near end");

        // After 10 ticks: expired and removed
        flashes.tick();
        assertEquals(0, flashes.count(), "flash expires and is removed at duration");
    }

    @Test
    void idRangeIsExclusivelyReservedNegative() {
        for (int i = 0; i < ExplosionFlashes.MAX_FLASHES; i++) {
            flashes.add(i * 10.0, 64.0, 0.0, 4.0f);
        }
        assertEquals(ExplosionFlashes.MAX_FLASHES, flashes.count());
        for (int i = 0; i < flashes.count(); i++) {
            int id = flashes.id(i);
            assertTrue(id <= ExplosionFlashes.FIRST_ID && id >= ExplosionFlashes.LAST_ID,
                "flash id " + id + " must be in reserved negative range [" + ExplosionFlashes.LAST_ID + ", " + ExplosionFlashes.FIRST_ID + "]");
        }
    }

    @Test
    void capEnforcedWithoutLeaking() {
        // Add more than MAX_FLASHES
        for (int i = 0; i < ExplosionFlashes.MAX_FLASHES + 10; i++) {
            flashes.add(i * 5.0, 64.0, i * 5.0, 3.0f);
        }
        assertEquals(ExplosionFlashes.MAX_FLASHES, flashes.count(), "count must not exceed MAX_FLASHES");

        // Tick several times and add more
        for (int i = 0; i < 5; i++) flashes.tick();
        for (int i = 0; i < 10; i++) flashes.add(100.0, 64.0, 100.0, 4.0f);
        assertEquals(ExplosionFlashes.MAX_FLASHES, flashes.count());
    }

    @Test
    void powerScalesDuration() {
        // Small explosion (power 1.0) vs medium (power 4.0) vs large (power 6.0)
        flashes.add(0.0, 64.0, 0.0, 1.0f);
        int smallDuration = flashes.duration(0);

        flashes.reset();
        flashes.add(0.0, 64.0, 0.0, 4.0f);
        int mediumDuration = flashes.duration(0);

        flashes.reset();
        flashes.add(0.0, 64.0, 0.0, 6.0f);
        int largeDuration = flashes.duration(0);

        assertTrue(smallDuration < mediumDuration, "small explosion duration (" + smallDuration + ") < medium (" + mediumDuration + ")");
        assertTrue(mediumDuration < largeDuration, "medium explosion duration (" + mediumDuration + ") < large (" + largeDuration + ")");
    }

    @Test
    void resetClearsAllFlashes() {
        flashes.add(0.0, 64.0, 0.0, 4.0f);
        flashes.add(10.0, 64.0, 10.0, 4.0f);
        assertEquals(2, flashes.count());
        flashes.reset();
        assertEquals(0, flashes.count());
    }

    @Test
    void collectAddsSourcesToDynamicLights() {
        DynamicLights lights = new DynamicLights();
        flashes.add(5.5, 64.5, 5.5, 4.0f);
        lights.begin();
        flashes.collect(lights);
        lights.end(15, 0.25f, 1, (a, b, c, d, e, f) -> {});
        assertEquals(15, lights.at(5, 64, 5), "dynamic light is present at flash location");
    }
}
