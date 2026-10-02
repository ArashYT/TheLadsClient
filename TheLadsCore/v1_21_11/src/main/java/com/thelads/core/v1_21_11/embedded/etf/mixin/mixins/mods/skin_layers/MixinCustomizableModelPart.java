package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.mods.skin_layers;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tr7zw.skinlayers.render.CustomizableModelPart;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.MixinModelPart;
import com.thelads.core.v1_21_11.embedded.etf.features.texture_handlers.ETFTexture;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFVertexConsumer;
import com.thelads.core.v1_21_11.embedded.etf.utils.URenderTypeToVertexConsumer;

/**
 * this is a copy of {@link MixinModelPart}
 */
@Pseudo
@Mixin(value = CustomizableModelPart.class) // implements Mesh
public abstract class MixinCustomizableModelPart {

    @Shadow
    public abstract void render(final ModelPart vanillaModel, final PoseStack poseStack, final VertexConsumer vertexConsumer, final int light, final int overlay, final int color);

    @Inject(method =
            "render(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "HEAD")
    )
    private void etf$findOutIfInitialModelPart(CallbackInfo ci) {
        if (ETF.config().getConfig().use3DSkinLayerPatch) {
            ETFState.currentModelPartDepth++;
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private VertexConsumer etf$modify(final VertexConsumer value) {
        if (value instanceof BufferBuilder builder && !builder.building){
            if (value instanceof ETFVertexConsumer etf
                    && etf.etf$getRenderLayer() != null
                    && etf.etf$getProvider() != null){
                return etf.etf$getProvider().getBuffer(etf.etf$getRenderLayer());
            }
        }
        return value;
    }

    @Inject(method =
            "render(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "RETURN")
    )
    private void etf$doEmissive(
            final ModelPart vanillaModel, final PoseStack poseStack, final VertexConsumer vertexConsumer, final int light, final int overlay, final int color, final CallbackInfo ci
            ) {
        if (ETF.config().getConfig().use3DSkinLayerPatch) {
            //run code if this is the initial topmost rendered part
            if (ETFState.currentModelPartDepth != 1) {
                ETFState.currentModelPartDepth--;
            } else {
                if (ETFState.isStateActive()
                        && vertexConsumer instanceof ETFVertexConsumer etfVertexConsumer) {
                    ETFTexture texture = etfVertexConsumer.etf$getETFTexture();
                    if (texture != null && (texture.isEmissive() || texture.isEnchanted())) {
                        URenderTypeToVertexConsumer provider = etfVertexConsumer.etf$getProvider();
                        RenderType layer = etfVertexConsumer.etf$getRenderLayer();
                        if (provider != null && layer != null) {
                            //attempt special renders as eager OR checks
                            ETFUtils2.RenderMethodForOverlay renderer =
                                    (a, b) -> render(vanillaModel, poseStack, a, b, overlay,
                                            color
                                        );

                            if (ETFUtils2.renderEmissive(texture, provider, renderer) |
                                    ETFUtils2.renderEnchanted(texture, provider, light, renderer)) {
                                //reset render layer stuff behind the scenes if special renders occurred
                                provider.getBuffer(layer);
                            }
                        }
                    }
                }
                //ensure model count is reset
                ETFState.currentModelPartDepth = 0;
            }
        }
    }
}
