package com.thelads.smp.features.arise.nb2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint and lifecycle coordinator for Native Batch NB2 features.
 * Contains ZERO client-only references; fully dedicated-server safe.
 */
public final class NB2Features {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/NB2");

    private NB2Features() {}

    public static void init() {
        long start = System.currentTimeMillis();
        NB2Config config = NB2Config.load();
        PaxiManager.ensureDirectoriesExist();

        LOGGER.info("[NB2] Initialized Native Batch NB2 features in {} ms (Paxi={}, RealisticNametag={}, RemoveChatBracket={}, TooManyEntities={}, TradeUses={}, YungsMenuTweaks={})",
                System.currentTimeMillis() - start,
                config.paxi,
                config.realisticNametag,
                config.removeChatBracket,
                config.tooManyEntities,
                config.tradeUses,
                config.rightClickReversesCycleButtons);
    }
}
