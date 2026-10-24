package com.thelads.core.v26_2.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.thelads.core.perf.DeferredSky;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deferred sky rendering on 26.3 (DeferredSky, Polydium parity).
 * Discards occluded sky pixels behind opaque terrain in the Vulkan/RenderPearl pipeline.
 */
@Mixin(SkyRenderer.class)
public abstract class DeferredSkyMixin26 {
    @Inject(method = "render(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V", at = @At("HEAD"))
    private void lads$beforeSky(GpuBufferSlice slice, SkyRenderState state, CallbackInfo ci) {
        if (!DeferredSky.isEnabled()) return;
        DeferredSky.setActive(true);
    }

    @Inject(method = "render(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/SkyRenderState;)V", at = @At("RETURN"))
    private void lads$afterSky(GpuBufferSlice slice, SkyRenderState state, CallbackInfo ci) {
        if (!DeferredSky.isEnabled()) return;
        DeferredSky.setActive(false);
    }
}
