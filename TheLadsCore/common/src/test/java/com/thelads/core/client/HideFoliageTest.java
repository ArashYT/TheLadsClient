package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.modules.HideFoliageModule;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HideFoliageTest {
    @Test
    void offHidesNothing() {
        assertEquals(0, HideFoliage.mask(null));
        assertEquals(0, HideFoliage.mask(new HideFoliageModule()), "off by default");
    }

    @Test
    void groupsFollowTheirOptions() {
        HideFoliageModule module = new HideFoliageModule();
        module.setEnabled(true);
        module.seagrass.set(false);
        int mask = HideFoliage.mask(module);
        assertTrue(HideFoliage.hides(mask, HideFoliage.GRASS));
        assertTrue(HideFoliage.hides(mask, HideFoliage.LEAF_LITTER));
        assertFalse(HideFoliage.hides(mask, HideFoliage.SEAGRASS));
        assertFalse(HideFoliage.hides(mask, HideFoliage.TALL), "tall plants stay off by default");
    }

    @Test
    void tallPlantsAreInTheRiskySet() {
        assertTrue(HypixelSafetyManager.isSingleplayerOnlyModule(HideFoliage.TALL_GATE));
    }

    @Test
    void idsAreExactAndNeverTheGrassBlock() {
        Set<String> seen = new HashSet<>();
        for (String[] group : HideFoliage.IDS)
            for (String id : group) {
                assertTrue(seen.add(id), "one group per id: " + id);
                assertFalse(id.contains(":"), "paths in the minecraft namespace");
            }
        assertFalse(seen.contains("grass_block"));
        assertEquals(HideFoliage.TALL + 1, HideFoliage.IDS.length);
    }
}
