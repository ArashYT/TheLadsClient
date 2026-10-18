package com.thelads.smp.features.arise.nb9.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Arise NB9 (Amendments-Style Vanilla Block Upgrades).
 * Strictly isolated behind Fabric client entrypoint so dedicated servers never load it.
 */
@Environment(EnvType.CLIENT)
public final class AriseNB9Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB9-Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[LadsSMP-NB9-Client] Initialized client renderer enhancements for Amendments block upgrades");
    }
}
