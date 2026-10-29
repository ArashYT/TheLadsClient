package com.thelads.core.v1_20_1.game.survival.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v1_20_1.game.survival.NativeSurvivalTweaks1201;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * SurvivalTweaks: Dimension brightness. Scales the brightness option the lightmap is built from (26.3 scales the same value in
 * LightmapRenderStateExtractor). Priority 900 runs it before Fullbright's mixin on the same value, so Fullbright still wins.
 */
@Mixin(value = LightTexture.class, priority = 900)
public abstract class SurvivalLightmapBrightnessMixin1201 {
    @ModifyExpressionValue(method = "updateLightTexture(F)V",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
        at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
    private float lads$modifyDimensionBrightness(float original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        return NativeSurvivalTweaks1201.active() && mod != null ? mod.brightness(original) : original;
    }
}
