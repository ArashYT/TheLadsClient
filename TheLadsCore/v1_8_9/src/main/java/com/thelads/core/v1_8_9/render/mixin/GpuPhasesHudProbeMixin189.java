package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.GpuPhases189;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * QA (-Dlads.gpuPhases=1): the vignette, one of the HUD's full-screen passes (GpuPhases189). Benchmarks against a client that
 * draws none (OneClient's options.txt has vignette:false) turn it off with -Dlads.vignette=false.
 */
@Mixin(GuiIngame.class)
public abstract class GpuPhasesHudProbeMixin189 {
    private static final boolean VIGNETTE = !"false".equalsIgnoreCase(System.getProperty("lads.vignette"));

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void lads$vignetteStart(float brightness, ScaledResolution resolution, CallbackInfo ci) {
        if (!VIGNETTE) ci.cancel();
        else if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.VIGNETTE);
    }

    @Inject(method = "renderVignette", at = @At("RETURN"))
    private void lads$vignetteEnd(float brightness, ScaledResolution resolution, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.VIGNETTE); }
}
