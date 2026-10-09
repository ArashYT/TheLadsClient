package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightTexture.class)
public class FullbrightMixin {
    @Shadow private boolean updateLightTexture;

    /** A Fullbright change rebuilds the lightmap in this frame, not at the next game tick that runs (none run in a menu). */
    @Inject(method = "updateLightTexture(F)V", at = @At("HEAD"))
    private void lads$gammaChanged(float partialTick, CallbackInfo callback) {
        if (NativeFeatures1201.gammaChanged()) updateLightTexture = true;
    }

    @ModifyExpressionValue(method = "updateLightTexture(F)V",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
        at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0))
    private float lads$lightmapGamma(float gamma) {
        return NativeFeatures1201.gamma(gamma);
    }
}
