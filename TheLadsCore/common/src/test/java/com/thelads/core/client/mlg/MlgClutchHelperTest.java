package com.thelads.core.client.mlg;

import com.thelads.core.modules.MlgCobwebModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MlgClutchHelperTest {
    @Test
    void testDefaults() {
        MlgCobwebModule mod = new MlgCobwebModule();
        assertFalse(mod.isEnabled());
        assertTrue(mod.waterBucket.get());
        assertTrue(mod.cobweb.get());
        assertTrue(mod.slimeBlock.get());
        assertEquals(3, mod.minFallDistance.getIntValue());
    }

    @Test
    void testClutchItemIdentification() {
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:water_bucket"));
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:cobweb"));
        assertTrue(MlgClutchHelper.isClutchItem("minecraft:slime_block"));
        assertFalse(MlgClutchHelper.isClutchItem("minecraft:dirt"));
        assertFalse(MlgClutchHelper.isClutchItem(null));
    }

    @Test
    void testLandingPrediction() {
        assertNull(MlgClutchHelper.predictLanding(10.5, 60.0, 20.5, 0.0, 1.0f, 3));
        var pred = MlgClutchHelper.predictLanding(10.5, 60.0, 20.5, -1.0, 5.0f, 3);
        assertNotNull(pred);
        assertEquals(10, pred.x());
        assertEquals(20, pred.z());
        assertEquals(58, pred.y());
        assertFalse(pred.fatal());

        var fatalPred = MlgClutchHelper.predictLanding(10.5, 60.0, 20.5, -1.5, 15.0f, 3);
        assertNotNull(fatalPred);
        assertTrue(fatalPred.fatal());
    }
}
