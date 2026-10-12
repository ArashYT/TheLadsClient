package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright under a shader pack: Iris packs light the world themselves and never see the lightmap's gamma, but they brighten it
 * under Night Vision, whose strength Iris reads here for the camera (with or without the effect). With Fullbright on, the player's
 * own reads full strength. Vanilla asks only while the effect is on (else this method would fail), so it is unchanged.
 */
@Mixin(GameRenderer.class)
abstract class FullbrightShadersMixin {
    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lads$fullbrightShaders(LivingEntity entity, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (entity != null && entity == Minecraft.getInstance().player && !entity.hasEffect(MobEffects.NIGHT_VISION) && NativeFeatures.fullbrightGamma() >= 0)
            cir.setReturnValue(1F);
    }
}
