package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering.feature;


import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;

import net.minecraft.client.renderer.RenderType;

@Mixin(SlimeOuterLayer.class)
public class MixinSlimeOverlayFeatureRenderer {



    @Inject(method =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "HEAD"))
    private void emf$setLayerForOverrides(CallbackInfo ci) {
        if (EMFState.state() == null) return;
        EMFState.state().setLayerFactory(
                RenderType
                ::entityTranslucent);
    }
}
