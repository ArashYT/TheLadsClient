package com.thelads.core.perf;

import com.thelads.core.config.BoolOption;
import com.thelads.core.modules.PerformanceManagerModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Perf189SettingsTest {
    @Test
    void testOptionSync() {
        PerformanceManagerModule module = new PerformanceManagerModule();

        BoolOption parallax = (BoolOption) module.getOption("Camera Parallax Fix");

        assertFalse(Perf189Settings.isCameraParallaxFix());

        parallax.set(true);
        assertTrue(Perf189Settings.isCameraParallaxFix());
        parallax.set(false);
        assertFalse(Perf189Settings.isCameraParallaxFix());
    }
}
