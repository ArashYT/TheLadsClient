package com.thelads.smp.features.arise.nb8;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint initializer for Batch NB8: Vein Mining.
 */
public final class VeinMiningFeatures {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/NB8");

    public static void init() {
        long start = System.currentTimeMillis();
        AriseNB8Config.load();
        VeinMiningNetwork.init();
        VeinMiningManager.init();
        LOGGER.info("[Arise NB8] Vein Mining feature initialized in {}ms (enabled={}, maxBlocks={}, blocksPerTick={}, exhaustionMultiplier={})",
                System.currentTimeMillis() - start,
                AriseNB8Config.get().enabled,
                AriseNB8Config.get().maxBlocks,
                AriseNB8Config.get().blocksPerTick,
                AriseNB8Config.get().exhaustionMultiplier);
    }
}
