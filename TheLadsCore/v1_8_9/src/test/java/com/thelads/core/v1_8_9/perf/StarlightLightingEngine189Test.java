package com.thelads.core.v1_8_9.perf;

import com.thelads.core.perf.Perf189Settings;
import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StarlightLightingEngine189Test {
    @Before
    public void setUp() {
        StarlightLightingEngine189.clear();
        Perf189Settings.starlightLighting = false;
    }

    @Test
    public void testQueueDisabledByDefault() {
        assertFalse(StarlightLightingEngine189.isEnabled());
        assertFalse(StarlightLightingEngine189.onCheckLightFor(null, EnumSkyBlock.BLOCK, new BlockPos(0, 64, 0)));
        assertEquals(0, StarlightLightingEngine189.getPendingCount());
    }

    @Test
    public void testQueueEnqueueWhenEnabled() {
        Perf189Settings.starlightLighting = true;
        assertTrue(StarlightLightingEngine189.isEnabled());
        boolean handled = StarlightLightingEngine189.onCheckLightFor(null, EnumSkyBlock.BLOCK, new BlockPos(10, 64, 10));
        assertTrue(handled);
        assertEquals(1, StarlightLightingEngine189.getPendingCount());

        // Process queue with null world -> safely returns 0 without crashing
        int processed = StarlightLightingEngine189.processBatch(null, 100);
        assertEquals(0, processed);
        assertEquals(1, StarlightLightingEngine189.getPendingCount());

        StarlightLightingEngine189.clear();
        assertEquals(0, StarlightLightingEngine189.getPendingCount());
    }
}
