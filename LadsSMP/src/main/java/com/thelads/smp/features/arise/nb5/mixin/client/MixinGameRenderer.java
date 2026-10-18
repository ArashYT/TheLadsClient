package com.thelads.smp.features.arise.nb5.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.features.arise.nb5.client.ParkourAnimationManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into GameRenderer to apply first-person camera pitch rotation and vertical dips.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(
            method = "bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL")
    )
    private void ladssmp$applyFirstPersonCameraParkour(CameraRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ParkourAnimationManager.applyCameraTransform(state, poseStack);
    }
}
