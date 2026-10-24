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

        BoolOption instancing = (BoolOption) module.getOption("GPU Entity Instancing");
        BoolOption starlight = (BoolOption) module.getOption("Starlight Lighting Engine");
        BoolOption parallax = (BoolOption) module.getOption("Camera Parallax Fix");
        BoolOption culling = (BoolOption) module.getOption("Chunk Culling Fix");

        assertFalse(Perf189Settings.isGpuEntityInstancing());
        assertFalse(Perf189Settings.isStarlightLighting());
        assertFalse(Perf189Settings.isCameraParallaxFix());
        assertFalse(Perf189Settings.isChunkCullingFix());

        instancing.set(true);
        assertTrue(Perf189Settings.isGpuEntityInstancing());
        instancing.set(false);
        assertFalse(Perf189Settings.isGpuEntityInstancing());

        starlight.set(true);
        assertTrue(Perf189Settings.isStarlightLighting());
        starlight.set(false);
        assertFalse(Perf189Settings.isStarlightLighting());

        parallax.set(true);
        assertTrue(Perf189Settings.isCameraParallaxFix());
        parallax.set(false);
        assertFalse(Perf189Settings.isCameraParallaxFix());

        culling.set(true);
        assertTrue(Perf189Settings.isChunkCullingFix());
        culling.set(false);
        assertFalse(Perf189Settings.isChunkCullingFix());
    }
}
