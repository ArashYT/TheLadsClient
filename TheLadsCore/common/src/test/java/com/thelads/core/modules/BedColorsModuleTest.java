package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BedColorsModuleTest {
    @Test
    void testModuleDefaults() {
        BedColorsModule mod = new BedColorsModule();
        assertEquals("Bed Colors", mod.getName());
        assertTrue(mod.teamColors.get());
        assertTrue(mod.destructionVisuals.get());
        assertEquals(25, mod.particleCount.getIntValue());
        assertEquals(0xE74C3C, mod.customColor.getColor());
    }
}
