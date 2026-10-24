package com.thelads.core.client.survival;

import com.thelads.core.modules.SurvivalTweaksModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalGuardsTest {
    @Test
    void testDefaults() {
        SurvivalTweaksModule mod = new SurvivalTweaksModule();
        assertFalse(mod.isBlockToolAtLowDurabilityEnabled());
        assertEquals(5, mod.getLowDurabilityThreshold());
        assertFalse(mod.isAntiStripLogEnabled());
        assertFalse(mod.isAntiBuddingAmethystEnabled());
    }

    @Test
    void testGuardsWithoutModule() {
        // Without active module, nothing is blocked
        assertFalse(SurvivalGuards.shouldBlockTool(999, 1000));
        assertFalse(SurvivalGuards.shouldBlockStripLog(true, true, false));
        assertFalse(SurvivalGuards.shouldBlockBuddingAmethyst(true));
    }
}
