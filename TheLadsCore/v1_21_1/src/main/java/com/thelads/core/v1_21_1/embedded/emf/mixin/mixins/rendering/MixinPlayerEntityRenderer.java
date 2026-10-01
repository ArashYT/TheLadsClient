package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.rendering;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.emf.EMF;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import com.thelads.core.v1_21_1.embedded.emf.models.IEMFModel;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.math.EMFMath;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_1.embedded.emf.models.parts.EMFModelPartVanilla;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFEntity;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;




@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerEntityRenderer {


    @Inject(method = "renderHand", at = @At(value = "HEAD"))
    private void emf$setHandAnimState(CallbackInfo ci) {
        // Before visibility checks
        var state = EMFEntityRenderState.manualPlayerState();
        state.setIsFirstPersonHand(true);
        ETFState.mount(state);
    }

    @Inject(method = "renderHand", at = @At(value = "RETURN"))
    private void emf$unsetHand(final CallbackInfo ci) {
        var state = EMFState.state();
        if (state == null || !state.isManualPlayerState()) return;
        ETFState.unMount();
    }


}