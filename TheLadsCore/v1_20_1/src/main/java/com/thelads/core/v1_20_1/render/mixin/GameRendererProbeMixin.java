package com.thelads.core.v1_20_1.render.mixin;

import com.thelads.core.v1_20_1.render.RenderProbe1201;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA (1.8.5 render capture): each frame's render CPU time while RenderProbe1201 measures, and its finished frame. */
@Mixin(GameRenderer.class)
public abstract class GameRendererProbeMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void lads$start(float partialTick, long nanos, boolean renderLevel, CallbackInfo ci) { RenderProbe1201.frameStart(); }

    @Inject(method = "render(FJZ)V", at = @At("RETURN"))
    private void lads$end(float partialTick, long nanos, boolean renderLevel, CallbackInfo ci) { RenderProbe1201.frameEnd(); }
}
