package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v26_2.feature.VerticalBobState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class VerticalBobbingMixin {
    @Inject(method = "bobView", at = @At("TAIL"), require = 1)
    private void lads$verticalMotion(CameraRenderState state, PoseStack pose, CallbackInfo callback) {
        // Vanilla calls bobView only when View Bobbing is enabled. Both passes
        // read the same snapshot; drawing the hand cannot advance animation again.
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(((VerticalBobState) state).lads$verticalBob()));
    }
}
