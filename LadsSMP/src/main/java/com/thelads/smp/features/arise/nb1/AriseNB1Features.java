package com.thelads.smp.features.arise.nb1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Master initialization coordinator for Arise Native Batch NB1 features.
 * Wires configuration loading and lifecycle setup for all 15 batch features.
 */
public final class AriseNB1Features {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-AriseNB1");

    public static void init() {
        long start = System.currentTimeMillis();
        AriseNB1Config.load();

        AdminClaimsFeature.init();
        CarryOnExtendFeature.init();
        CompleteTurtleArmorFeature.init();
        CustomItemDespawnFeature.init();
        DimensionAccessFeature.init();
        QuicksandFeature.init();
        ExtraDungeonsFeature.init();
        FasterPortalsFeature.init();
        ForgivingBorderFeature.init();
        GameRuleManagerFeature.init();
        WeakerDayZombieFeature.init();
        WhyStacksOf16Feature.init();
        YouShallNotSpawnFeature.init();
        UsefulClipboardFeature.init();

        LOGGER.info("[Lads SMP] All Arise NB1 native features initialized in {} ms", System.currentTimeMillis() - start);
    }
}
