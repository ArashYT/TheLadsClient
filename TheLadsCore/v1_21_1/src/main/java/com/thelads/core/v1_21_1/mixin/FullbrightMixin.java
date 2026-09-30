package com.thelads.core.v1_21_1.mixin;
import com.thelads.core.v1_21_1.feature.NativeFeatures;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightTexture.class)
public class FullbrightMixin {
    @Redirect(method = "updateLightTexture(F)V",
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
        at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0), require = 1)
    private float ladsLightmapGamma(Double gamma) { return NativeFeatures.gamma(gamma.floatValue()); }
}
