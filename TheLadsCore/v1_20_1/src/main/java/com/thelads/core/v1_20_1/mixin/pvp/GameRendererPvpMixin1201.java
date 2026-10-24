package com.thelads.core.v1_20_1.mixin.pvp;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render Tweaks' Bobbing Disconnect: View Bobbing moves only your hand. bobView runs for the world (renderLevel) and again for the
 * hand (renderItemInHand); only the world's call is skipped. Totem Animation: hide the pop or scale the floating totem.
 */
@Mixin(GameRenderer.class)
public class GameRendererPvpMixin1201 {
    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private boolean lads$worldBob(GameRenderer renderer, PoseStack pose, float partialTick) {
        return !PvpVisuals.bobbingDisconnect;
    }

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void lads$hideTotem(int width, int height, float partialTick, CallbackInfo ci) {
        if (PvpVisuals.hideTotem) ci.cancel();
    }

    @WrapOperation(method = "renderItemActivationAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void lads$totemScale(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        float s = PvpVisuals.totemScale;
        original.call(pose, x * s, y * s, z * s);
    }
}
