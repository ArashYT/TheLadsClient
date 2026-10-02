package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeOldAnimations;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Low Fire: both first-person fire quads sit lower (through the loop's own translate, so nothing leaks past renderFire). */
@Mixin(ScreenEffectRenderer.class)
public class OldAnimationsFireMixin {
    @ModifyArg(method = "renderFire", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"), index = 1, require = 1)
    private static float lads$lowFire(float y) {
        return NativeOldAnimations.fireY(y);
    }
}
