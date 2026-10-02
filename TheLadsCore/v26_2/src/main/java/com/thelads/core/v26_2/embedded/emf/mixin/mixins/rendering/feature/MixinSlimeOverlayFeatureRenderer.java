package com.thelads.core.v26_2.embedded.emf.mixin.mixins.rendering.feature;


import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFState;

import net.minecraft.client.renderer.rendertype.RenderTypes;

@Mixin(SlimeOuterLayer.class)
public class MixinSlimeOverlayFeatureRenderer {



    @Inject(method =
            "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/SlimeRenderState;FF)V",
            at = @At(value = "HEAD"))
    private void emf$setLayerForOverrides(CallbackInfo ci) {
        if (EMFState.state() == null) return;
        EMFState.state().setLayerFactory(
                RenderTypes
                ::entityTranslucent);
    }
}
