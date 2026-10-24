package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeferredSkyTest {
    @BeforeEach
    void setUp() {
        DeferredSky.setDisabled(false);
        DeferredSky.setActive(false);
    }

    @Test
    void tracksActiveState() {
        assertFalse(DeferredSky.isActive());
        DeferredSky.setActive(true);
        assertTrue(DeferredSky.isActive());
    }

    @Test
    void tracksEnabledState() {
        assertTrue(DeferredSky.isEnabled());
        DeferredSky.setDisabled(true);
        assertFalse(DeferredSky.isEnabled());
    }
}
