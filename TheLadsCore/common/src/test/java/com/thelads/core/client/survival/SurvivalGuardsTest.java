package com.thelads.core.client.survival;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.SurvivalTweaksModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalGuardsTest {
    @AfterEach
    void tearDown() {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (mod != null) {
            mod.blockToolAtLowDurability.set(false);
            mod.antiStripLog.set(false);
            mod.antiBuddingAmethyst.set(false);
            mod.lowDurabilityThreshold.setValue(3);
        }
    }

    @Test
    void testDefaults() {
        SurvivalTweaksModule mod = new SurvivalTweaksModule();
        assertFalse(mod.isBlockToolAtLowDurabilityEnabled());
        assertEquals(3, mod.getLowDurabilityThreshold());
        assertFalse(mod.isAntiStripLogEnabled());
        assertFalse(mod.isAntiBuddingAmethystEnabled());
    }

    @Test
    void testGuardsActiveBehavior() {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        assertNotNull(mod);
        mod.setEnabled(true);
        mod.blockToolAtLowDurability.set(true);
        mod.lowDurabilityThreshold.setValue(5);
        mod.antiStripLog.set(true);
        mod.antiBuddingAmethyst.set(true);

        // Tool guard: remaining durability <= 5 -> blocked
        assertTrue(SurvivalGuards.shouldBlockTool(996, 1000)); // 4 remaining
        assertTrue(SurvivalGuards.shouldBlockTool(995, 1000)); // 5 remaining
        assertFalse(SurvivalGuards.shouldBlockTool(994, 1000)); // 6 remaining

        // Strip log guard: axe + log + not sneaking -> blocked
        assertTrue(SurvivalGuards.shouldBlockStripLog(true, true, false));
        assertFalse(SurvivalGuards.shouldBlockStripLog(true, true, true)); // sneaking allowed
        assertFalse(SurvivalGuards.shouldBlockStripLog(false, true, false)); // no axe

        // Budding amethyst guard:
        assertTrue(SurvivalGuards.shouldBlockBuddingAmethyst(true));
        assertFalse(SurvivalGuards.shouldBlockBuddingAmethyst(false));
    }
}
