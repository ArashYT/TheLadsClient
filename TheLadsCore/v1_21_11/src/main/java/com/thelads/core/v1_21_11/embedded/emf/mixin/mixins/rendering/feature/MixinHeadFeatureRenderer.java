package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.feature;

import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;

@Mixin(CustomHeadLayer.class)
public class MixinHeadFeatureRenderer {

    private static final String RENDER = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V";


    @Inject(method = RENDER, at = @At(value = "HEAD"))
    private void emf$setHand(final CallbackInfo ci) {
        EMFState.isOnHead = true;
    }

    @Inject(method = RENDER, at = @At(value = "TAIL"))
    private void emf$unsetHand(final CallbackInfo ci) {
        EMFState.isOnHead = false;
    }

}