package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class SurvivalLightmapBrightnessMixin {
    @ModifyExpressionValue(
            method = "extract",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0)
    )
    private float lads$modifyDimensionBrightness(float original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null) {
            int pct = mod.dimensionBrightness.getIntValue();
            if (pct != 100) {
                return original * (pct / 100.0f);
            }
        }
        return original;
    }
}
