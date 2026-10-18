package com.thelads.smp.features.arise.nb10.client;

import com.thelads.smp.features.arise.nb10.AriseNB10Features;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Arise NB10 (Fairy Lights Hanging Decorations).
 * Registers the BlockEntityRenderer for fasteners.
 */
public final class AriseNB10Client implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-NB10Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[LadsSMP-NB10Client] Registering FastenerBlockEntityRenderer...");
        BlockEntityRendererRegistry.register(AriseNB10Features.FASTENER_BLOCK_ENTITY_TYPE, FastenerBlockEntityRenderer::new);
        LOGGER.info("[LadsSMP-NB10Client] FastenerBlockEntityRenderer registered successfully.");
    }
}
