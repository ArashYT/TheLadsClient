package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HitColorDamageTypeTest {
    @Test
    void testDamageTypeClassification() {
        assertEquals(HitColorModule.DamageType.FIRE, HitColorModule.classifyDamageSource("inFire", true, false, false));
        assertEquals(HitColorModule.DamageType.FIRE, HitColorModule.classifyDamageSource("lava", false, false, false));
        assertEquals(HitColorModule.DamageType.FIRE, HitColorModule.classifyDamageSource("onFire", false, false, false));

        assertEquals(HitColorModule.DamageType.FALL, HitColorModule.classifyDamageSource("fall", false, true, false));
        assertEquals(HitColorModule.DamageType.DROWNING, HitColorModule.classifyDamageSource("drown", false, false, true));

        assertEquals(HitColorModule.DamageType.DEFAULT, HitColorModule.classifyDamageSource("player", false, false, false));
        assertEquals(HitColorModule.DamageType.DEFAULT, HitColorModule.classifyDamageSource("generic", false, false, false));
    }

    @Test
    void testDamageRecordAndExpiry() {
        HitColorModule.recordDamage(42, HitColorModule.DamageType.FIRE);
        assertEquals(HitColorModule.DamageType.FIRE, HitColorModule.getDamageType(42));

        HitColorModule.recordDamage(43, HitColorModule.DamageType.FALL);
        assertEquals(HitColorModule.DamageType.FALL, HitColorModule.getDamageType(43));

        HitColorModule.recordDamage(44, HitColorModule.DamageType.DROWNING);
        assertEquals(HitColorModule.DamageType.DROWNING, HitColorModule.getDamageType(44));

        assertEquals(HitColorModule.DamageType.DEFAULT, HitColorModule.getDamageType(9999));
    }

    @Test
    void testModuleColorLookup() {
        HitColorModule mod = new HitColorModule();

        // With perDamageType off, should return default color for all
        mod.perDamageType.set(false);
        int defaultCol = mod.getColor(HitColorModule.DamageType.DEFAULT);
        assertEquals(defaultCol, mod.getColor(HitColorModule.DamageType.FIRE));
        assertEquals(defaultCol, mod.getColor(HitColorModule.DamageType.FALL));

        // With perDamageType on, should return source-specific colors
        mod.perDamageType.set(true);
        assertNotEquals(0, mod.getColor(HitColorModule.DamageType.FIRE));
        assertNotEquals(0, mod.getColor(HitColorModule.DamageType.FALL));
        assertNotEquals(0, mod.getColor(HitColorModule.DamageType.DROWNING));
    }
}
