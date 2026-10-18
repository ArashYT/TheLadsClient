package com.thelads.smp.features.arise.nb7.client;

import com.thelads.smp.features.arise.nb7.BiomeCreeperVariant;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client entrypoint for Arise NB7.
 * Strictly registered behind Fabric client entrypoint so dedicated servers never load it.
 */
@Environment(EnvType.CLIENT)
public final class AriseNB7Client implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        for (BiomeCreeperVariant variant : BiomeCreeperVariant.values()) {
            if (variant.getEntityType() != null) {
                EntityRendererRegistry.register(variant.getEntityType(), context -> new BiomeCreeperRenderer(context, variant.getTextureLocation()));
            }
        }
    }
}
