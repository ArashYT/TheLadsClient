package com.thelads.core.v1_8_9.render.mixin;

import com.thelads.core.v1_8_9.render.RenderProbeHooks189;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA (1.8.5 render capture): the CPU time of each frame's world and GUI rendering while the probe measures. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererProbeMixin {
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void lads$start(float partialTicks, long nanoTime, CallbackInfo ci) { RenderProbeHooks189.frameStart(); }

    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void lads$end(float partialTicks, long nanoTime, CallbackInfo ci) { RenderProbeHooks189.frameEnd(); }
}
