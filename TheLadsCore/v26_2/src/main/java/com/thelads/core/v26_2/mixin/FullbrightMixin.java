package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.NativeFeatures;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class FullbrightMixin {
    @Shadow private boolean needsUpdate;

    /** A Fullbright change rebuilds the lightmap in this frame (see NativeFeatures.gammaChanged), not at the next tick that runs. */
    @Inject(method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V", at = @At("HEAD"), require = 1)
    private void ladsGammaChanged(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (NativeFeatures.gammaChanged()) needsUpdate = true;
    }

    @Redirect(method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V",
        slice = @Slice(from = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
        at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0), require = 1)
    private float ladsLightmapGamma(Double gamma) { return NativeFeatures.gamma(gamma.floatValue()); }
}
