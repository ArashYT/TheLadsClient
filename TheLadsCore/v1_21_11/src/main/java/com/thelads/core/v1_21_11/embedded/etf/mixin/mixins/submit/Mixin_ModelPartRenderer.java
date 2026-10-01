package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.submit;

import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;

@Mixin(ModelPartFeatureRenderer.class)
public class Mixin_ModelPartRenderer {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;last()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"))
    private void emf$initRender(final CallbackInfo ci, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        var light = modelSubmit.lightCoords();
        if (light == ETF.EMISSIVE_FEATURE_LIGHT_VALUE || light == ETF.EYES_FEATURE_LIGHT_VALUE) {
            ETFState.startSpecialRenderOverlayPhase();
        }

    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelPartSubmit;crumblingOverlay()Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;"))
    private void emf$endRender(final CallbackInfo ci, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        var light = modelSubmit.lightCoords();
        if (light == ETF.EMISSIVE_FEATURE_LIGHT_VALUE || light == ETF.EYES_FEATURE_LIGHT_VALUE) {
            ETFState.endSpecialRenderOverlayPhase();
        }
    }

}