package com.thelads.core.v26_2.mixin.pvp;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Render Tweaks, Bobbing Disconnect: bobView runs for the world (renderLevel) and for the hand (renderItemInHand); the world's is skipped. */
@Mixin(GameRenderer.class)
public class GameRendererBobMixin26 {
    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView("
        + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private boolean lads$worldBob(GameRenderer renderer, CameraRenderState camera, PoseStack pose) {
        return !PvpVisuals.bobbingDisconnect;
    }
}
