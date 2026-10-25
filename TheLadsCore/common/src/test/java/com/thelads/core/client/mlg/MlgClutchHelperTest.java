package com.thelads.core.client.mlg;

import com.thelads.core.modules.MlgCobwebModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MlgClutchHelperTest {
    @Test
    void testClutchItemDetection() {
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:water_bucket"));
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:bucket_water"));
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:web"));
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:cobweb"));
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:slime_block"));
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:hay_block"));
        assertFalse(MlgClutchHelper.isClutchItem("minecraft:diamond_sword"));
        assertFalse(MlgClutchHelper.isClutchItem(null));
    }

    @Test
    void testLandingPrediction() {
        // Not falling fast enough
        assertNull(MlgClutchHelper.predictLanding(10.0, 70.0, 10.0, 0.0, 0.0f, 4));
        assertNull(MlgClutchHelper.predictLanding(10.0, 70.0, 10.0, -0.5, 2.0f, 4));

        // Valid fall
        MlgClutchHelper.LandingPrediction pred = MlgClutchHelper.predictLanding(10.5, 80.0, 20.5, -0.8, 6.0f, 4);
        assertNotNull(pred);
        assertEquals(10, pred.x());
        assertEquals(20, pred.z());
        assertTrue(pred.distanceToLanding() > 0);
        assertFalse(pred.fatal()); // 6 fall distance < 12

        // Fatal fall
        MlgClutchHelper.LandingPrediction fatalPred = MlgClutchHelper.predictLanding(10.5, 80.0, 20.5, -1.2, 15.0f, 4);
        assertNotNull(fatalPred);
        assertTrue(fatalPred.fatal());
    }

    @Test
    void testModuleDefaults() {
        MlgCobwebModule mod = new MlgCobwebModule();
        assertEquals("MLG Cobweb", mod.getName());
        assertTrue(mod.waterBucket.get());
        assertTrue(mod.cobweb.get());
        assertTrue(mod.slimeBlock.get());
        assertTrue(mod.hayBale.get());
        assertTrue(mod.timingReticle.get());
        assertTrue(mod.ghostBox.get());
        assertEquals(4, mod.minFallDistance.getIntValue());
    }
}
