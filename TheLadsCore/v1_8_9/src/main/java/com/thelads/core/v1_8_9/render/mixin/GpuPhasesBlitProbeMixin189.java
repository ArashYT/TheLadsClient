package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.GpuPhases189;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA (-Dlads.gpuPhases=1): the main framebuffer's copy to the window (GpuPhases189). */
@Mixin(Framebuffer.class)
public abstract class GpuPhasesBlitProbeMixin189 {
    @Inject(method = "framebufferRender", at = @At("HEAD"))
    private void lads$blitStart(int width, int height, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.begin(GpuPhases189.BLIT); }

    @Inject(method = "framebufferRender", at = @At("RETURN"))
    private void lads$blitEnd(int width, int height, CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.end(GpuPhases189.BLIT); }
}
