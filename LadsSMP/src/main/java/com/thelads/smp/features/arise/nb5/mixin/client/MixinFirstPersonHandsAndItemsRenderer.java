package com.thelads.smp.features.arise.nb5.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.smp.features.arise.nb5.client.ParkourAnimationManager;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into FirstPersonHandsAndItemsRenderer to offset hands in first-person view
 * during sliding, vaulting, and ledge hanging.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class MixinFirstPersonHandsAndItemsRenderer {

    @Inject(
            method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
            at = @At("HEAD")
    )
    private void ladssmp$applyFirstPersonHandsParkour(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerRenderState, FirstPersonHandsAndItemsRenderState firstPersonHandsAndItemsRenderState, CallbackInfo ci) {
        ParkourAnimationManager.applyFirstPersonHandsTransform(poseStack);
    }
}
