package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;
import com.thelads.core.v1_21_11.embedded.emf.models.IEMFModel;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.math.EMFMath;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_11.embedded.emf.models.parts.EMFModelPartVanilla;
import com.thelads.core.v1_21_11.embedded.emf.utils.EMFEntity;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_11.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_11.embedded.etf.utils.ETFEntity;


import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;


@Mixin(value = AvatarRenderer.class, priority = 1100) // priority ensures the first person hand state wraps ETF's submits properly
public abstract class MixinPlayerEntityRenderer<AvatarlikeEntity extends Avatar & ClientAvatarEntity>
        extends LivingEntityRenderer<AvatarlikeEntity, AvatarRenderState, PlayerModel> {

    public MixinPlayerEntityRenderer() { super(null, null, 0); }


    @Inject(method = "renderHand", at = @At(value = "HEAD"))
    private void emf$setHandAnimState(CallbackInfo ci) {
        // Before visibility checks
        var state = EMFEntityRenderState.manualPlayerState();
        state.setIsFirstPersonHand(true);
        ETFState.mount(state);
    }
    @Inject(method = "renderHand", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"
    ))
    private void emf$setHandAnims(CallbackInfo ci, @Local(argsOnly = true) ModelPart modelPart) {
        // flag this for later submit render
        if (modelPart instanceof EMFModelPartVanilla vanilla) {
            vanilla.isPlayerArm = true;
            // Position now for mods that need it
            vanilla.getRoot().animate();
        }

    }

    @Inject(method = "renderHand", at = @At(value = "RETURN"))
    private void emf$unsetHand(final CallbackInfo ci) {
        var state = EMFState.state();
        if (state == null || !state.isManualPlayerState()) return;
        ETFState.unMount();
    }


}