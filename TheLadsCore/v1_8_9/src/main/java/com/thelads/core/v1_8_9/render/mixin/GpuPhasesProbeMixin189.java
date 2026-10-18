package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.GpuPhases189;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA (-Dlads.gpuPhases=1): frame start and the swap for GpuPhases189. */
@Mixin(Minecraft.class)
public abstract class GpuPhasesProbeMixin189 {
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void lads$gpuFrame(CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.frameStart(); }

    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void lads$gpuSwap(CallbackInfo ci) { if (GpuPhases189.ON) GpuPhases189.beforeSwap(); }
}
