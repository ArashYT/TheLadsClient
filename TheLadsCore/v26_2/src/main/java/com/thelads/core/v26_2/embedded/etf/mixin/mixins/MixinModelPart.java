package com.thelads.core.v26_2.embedded.etf.mixin.mixins;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.mixin.mixins.mods.sodium.MixinModelPartSodium;
import com.thelads.core.v26_2.embedded.etf.features.texture_handlers.ETFTexture;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v26_2.embedded.etf.utils.ETFVertexConsumer;
import com.thelads.core.v26_2.embedded.etf.utils.URenderTypeToVertexConsumer;

/**
 * this method figures out if a {@link ModelPart} is the top level of the children tree being rendered,
 * then applies overlay rendering like emissives and enchanted pixels.
 * <p>
 * this is copied in {@link MixinModelPartSodium} for sodium's alternative model part render method.
 * <p>
 * the priority is set so this method will never run before sodium cancels the vanilla rendering code.
 */
@Mixin(value = ModelPart.class, priority = 2000)
public abstract class MixinModelPart {

    @Shadow public abstract void render(final PoseStack poseStack, final VertexConsumer vertexConsumer, final int i, final int j, final int k);





    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",at = @At(value = "HEAD"))
    private void etf$findOutIfInitialModelPart(final PoseStack poseStack, final VertexConsumer vertexConsumer, final int i, final int j, final int k, final CallbackInfo ci) {
        ETFState.currentModelPartDepth++;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",at = @At(value = "RETURN"))
    private void etf$doEmissiveIfInitialPart(final PoseStack matrices, final VertexConsumer vertices, final int light, final int overlay, final int k, final CallbackInfo ci) {
        //run code if this is the initial topmost rendered part
        if (ETFState.currentModelPartDepth > 1) {
            ETFState.currentModelPartDepth--;
        } else {
            //top level model so try special rendering
            if (ETFState.isStateActive() &&
                    vertices instanceof ETFVertexConsumer etfVertexConsumer) {
                ETFTexture texture = etfVertexConsumer.etf$getETFTexture();
                //is etf texture not null and does it special render?
                if (texture != null && (texture.isEmissive() || texture.isEnchanted())) {
                    URenderTypeToVertexConsumer provider = etfVertexConsumer.etf$getProvider();
                    //very important this is captured before doing the special renders as they can potentially modify
                    //the same ETFVertexConsumer down stream
                    RenderType layer = etfVertexConsumer.etf$getRenderLayer();
                    //are these render required objects valid?
                    if (provider != null && layer != null) {
                        //attempt special renders as eager OR checks
                        ETFUtils2.RenderMethodForOverlay renderer = (a, b) -> render(matrices, a, b, overlay,
                                    k
                                );
                        if (ETFUtils2.renderEmissive(texture, provider, renderer)
                                | ETFUtils2.renderEnchanted(texture, provider, light, renderer)
                        ) {
                            //reset render layer stuff behind the scenes if special renders occurred
                            //this will also return ETFVertexConsumer held data to normal if the same ETFVertexConsumer
                            //was previously affected by a special render
                        }
                    }
                }
            }
            //ensure model count is reset
            ETFState.currentModelPartDepth = 0;
        }
    }


    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
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

}






