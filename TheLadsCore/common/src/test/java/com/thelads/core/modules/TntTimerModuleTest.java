package com.thelads.core.modules;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TntTimerModuleTest {
    @Test void theTimeLeftReadsAsSecondsWithTwoDecimals() {
        assertEquals("4.00s", TntTimerModule.label(80));
        assertEquals("3.45s", TntTimerModule.label(69));
        assertEquals("3.42s", TntTimerModule.label(68.4f));
        assertEquals("0.05s", TntTimerModule.label(1));
        assertEquals("0.00s", TntTimerModule.label(0.04f));
        assertEquals("0.00s", TntTimerModule.label(-3), "never below zero");
        assertEquals("12.50s", TntTimerModule.label(250));
    }

    @Test void theColourSlidesFromGreenThroughYellowToRed() {
        assertEquals(0xFF00FF00, TntTimerModule.color(80));
        assertEquals(0xFF00FF00, TntTimerModule.color(400), "a long fuse stays green");
        assertEquals(0xFF80FF00, TntTimerModule.color(60));
        assertEquals(0xFFFFFF00, TntTimerModule.color(40));
        assertEquals(0xFFFF8000, TntTimerModule.color(20));
        assertEquals(0xFFFF0000, TntTimerModule.color(0));
    }

    @Test void itShowsWithinNameTagRangeUnlessThroughBlocks() {
        var module = new TntTimerModule();
        assertEquals(List.of("Size", "Background", "Text Shadow", "TNT minecarts", "Through blocks"),
            module.getOptions().stream().map(Option::getName).toList());
        assertTrue(module.isEnabled() && module.background.get() && module.shadow.get() && module.minecarts.get() && !module.throughBlocks.get());
        assertTrue(module.shows(64 * 64));
        assertFalse(module.shows(64 * 64 + 1));
        module.throughBlocks.set(true);
        assertTrue(module.shows(1e9));
        com.thelads.core.client.HypixelSafetyManager.qaOverride = true; // on Hypixel: never through walls (it would be ESP)
        try {
            assertFalse(module.seeThrough());
            assertFalse(module.shows(64 * 64 + 1));
        } finally {
            com.thelads.core.client.HypixelSafetyManager.qaOverride = false;
        }
        assertEquals(0.025f, module.scale(), 1e-7);
        module.size.setValue(150);
        assertEquals(0.0375f, module.scale(), 1e-7);
    }

    @Test void bothModulesAreRegistered() {
        assertInstanceOf(TntTimerModule.class, ModuleManager.getInstance().getModule(TntTimerModule.NAME));
        assertInstanceOf(WavyCapesModule.class, ModuleManager.getInstance().getModule(WavyCapesModule.NAME));
    }
}
