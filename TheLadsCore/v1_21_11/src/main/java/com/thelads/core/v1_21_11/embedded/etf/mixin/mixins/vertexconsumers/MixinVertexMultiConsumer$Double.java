package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.vertexconsumers;


import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.thelads.core.v1_21_11.embedded.etf.features.texture_handlers.ETFTexture;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFVertexConsumer;
import com.thelads.core.v1_21_11.embedded.etf.utils.URenderTypeToVertexConsumer;

/**
 * This allows a Double VertexConsumer to be used as an ETFVertexConsumer
 * mostly used for enchanted rendered things such as elytra to allow emissives to work correctly
 */
@Mixin(VertexMultiConsumer.Double.class)
public class MixinVertexMultiConsumer$Double implements ETFVertexConsumer {


    @Shadow @Final private VertexConsumer first;

    @Shadow @Final private VertexConsumer second;

    @Override
    public ETFTexture etf$getETFTexture() {
        if (second instanceof ETFVertexConsumer etfSecond) {
            return etfSecond.etf$getETFTexture();
        }
        if (first instanceof ETFVertexConsumer etfFirst) {
            return etfFirst.etf$getETFTexture();
        }
        return null;
    }

    @Override
    public URenderTypeToVertexConsumer etf$getProvider() {
        if (second instanceof ETFVertexConsumer etfSecond) {
            return etfSecond.etf$getProvider();
        }
        if (first instanceof ETFVertexConsumer etfFirst) {
            return etfFirst.etf$getProvider();
        }
        return null;
    }

    @Override
    public RenderType etf$getRenderLayer() {
        if (second instanceof ETFVertexConsumer etfSecond) {
            return etfSecond.etf$getRenderLayer();
        }
        if (first instanceof ETFVertexConsumer etfFirst) {
            return etfFirst.etf$getRenderLayer();
        }
        return null;
    }

    @Override
    public void etf$initETFVertexConsumer(URenderTypeToVertexConsumer provider, RenderType renderLayer) {

    }
}
