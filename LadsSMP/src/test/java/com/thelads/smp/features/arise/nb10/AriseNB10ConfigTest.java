package com.thelads.smp.features.arise.nb10;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AriseNB10ConfigTest {

    @Test
    void testConfigDefaults() {
        AriseNB10Config config = new AriseNB10Config();
        assertTrue(config.enabled);
        assertEquals(32, config.maxConnectionDistance);
        assertEquals(1.0F, config.sagMultiplier, 0.001F);
        assertTrue(config.windSway);
        assertEquals(64, config.renderDistance);
        assertEquals(14, config.bulbLightEmission);
        assertTrue(config.dropOnBreak);
        assertTrue(config.christmasJingles);
        assertEquals(40, config.jinglesAmplitude);
    }

    @Test
    void testValidationBoundaries() {
        AriseNB10Config config = new AriseNB10Config();

        config.maxConnectionDistance = 1;
        config.sagMultiplier = 0.01F;
        config.renderDistance = 5;
        config.bulbLightEmission = 20;
        config.jinglesAmplitude = 0;

        config.validate();

        assertEquals(4, config.maxConnectionDistance, "Max connection distance clamped to min 4");
        assertEquals(0.1F, config.sagMultiplier, 0.001F, "Sag multiplier clamped to min 0.1");
        assertEquals(16, config.renderDistance, "Render distance clamped to min 16");
        assertEquals(15, config.bulbLightEmission, "Bulb light clamped to max 15");
        assertEquals(1, config.jinglesAmplitude, "Jingle amplitude clamped to min 1");
    }
}
