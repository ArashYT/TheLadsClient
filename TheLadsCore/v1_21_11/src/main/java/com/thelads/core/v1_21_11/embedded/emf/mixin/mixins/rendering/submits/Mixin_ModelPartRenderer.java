package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering.submits;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPartVanilla;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;

@Mixin(ModelPartFeatureRenderer.class)
public class Mixin_ModelPartRenderer {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;last()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"))
    private void emf$initRender(final CallbackInfo ci, @Local SubmitNodeStorage.ModelPartSubmit modelSubmit) {
        EMFManager.getInstance().entityRenderCount++;


        if (modelSubmit.modelPart() instanceof EMFModelPartVanilla vanilla && vanilla.isPlayerArm) {
            if (Minecraft.getInstance().player != null) {
                EMFState.modelVariationIgnoresVisibility = true;
                var state = EMFEntityRenderState.manualPlayerState();
                if (state != null) {
                    state.setIsFirstPersonHand(true);
                    ETFState.mount(state);
                }
            } else {
                var state = EMFState.state();
                if (state != null) state.setIsFirstPersonHand(true);
            }

        }
    }

    @Inject(method = "render", at = @At(value = "TAIL"))
    private void emf$endRender(final CallbackInfo ci) {
        var state = EMFState.state();
        if (state != null && state.isManualPlayerState()) {
            ETFState.unMount();
        }
    }

}