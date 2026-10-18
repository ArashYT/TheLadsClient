package com.thelads.smp.features.arise.nb7.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.resources.Identifier;

/**
 * Client renderer for Biome Creeper variants. Reuses vanilla CreeperModel and CreeperPowerLayer.
 */
@Environment(EnvType.CLIENT)
public class BiomeCreeperRenderer extends CreeperRenderer {
    private final Identifier texture;

    public BiomeCreeperRenderer(EntityRendererProvider.Context context, Identifier texture) {
        super(context);
        this.texture = texture;
    }

    @Override
    public Identifier getTextureLocation(CreeperRenderState state) {
        return texture;
    }
}
