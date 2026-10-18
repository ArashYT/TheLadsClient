package com.thelads.smp.features.arise.nb5.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.features.arise.nb5.client.ParkourAnimationManager;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into AvatarRenderer to apply third-person roll rotations, dives, and body tilts.
 */
@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {

    @Inject(
            method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL")
    )
    private void ladssmp$applyThirdPersonParkourRotations(AvatarRenderState state, PoseStack poseStack, float f, float g, CallbackInfo ci) {
        ParkourAnimationManager.applyThirdPersonRotation(state, poseStack);
    }
}
