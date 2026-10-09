package com.thelads.core.v1_20_1.game.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.v1_20_1.game.NativeVerticalBob1201;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class VerticalBobbingMixin {
    // Vanilla calls bobView (for the world and for the hand) only with View Bobbing enabled and a player as the camera entity.
    @Inject(method = "bobView", at = @At("TAIL"))
    private void lads$verticalMotion(PoseStack pose, float partial, CallbackInfo callback) {
        float tip = NativeVerticalBob1201.displacement(partial);
        if (tip != 0) pose.mulPose(Axis.XP.rotationDegrees(tip));
    }
}
