package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.NativeFeatures;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LightmapRenderStateExtractor.class)
public class FullbrightMixin {
    @Redirect(method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
        at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0), require = 1)
    private float ladsLightmapGamma(Double gamma) { return NativeFeatures.gamma(gamma.floatValue()); }
}
