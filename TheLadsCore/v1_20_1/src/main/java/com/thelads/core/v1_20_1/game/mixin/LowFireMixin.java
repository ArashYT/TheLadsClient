package com.thelads.core.v1_20_1.game.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {
    // 1.7 Animations' Low Fire, in its own push so whatever is drawn after the fire stays put.
    @Inject(method = "renderFire(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"))
    private static void lads$lowFireBegin(Minecraft minecraft, PoseStack pose, CallbackInfo callback) {
        if (NativeOldAnimations1201.lowFireBegin()) {
            pose.pushPose();
            pose.translate(0.0f, -0.3f, 0.0f);
        }
    }

    @Inject(method = "renderFire(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"))
    private static void lads$lowFireEnd(Minecraft minecraft, PoseStack pose, CallbackInfo callback) {
        if (NativeOldAnimations1201.lowFireEnd()) pose.popPose();
    }
}
