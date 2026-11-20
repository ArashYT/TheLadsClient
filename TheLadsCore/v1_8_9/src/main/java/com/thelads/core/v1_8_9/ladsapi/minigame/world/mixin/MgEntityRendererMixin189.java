package com.thelads.core.v1_8_9.ladsapi.minigame.world.mixin;

import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_8_9.ladsapi.minigame.world.CameraRender189;
import com.thelads.core.v1_8_9.ladsapi.minigame.world.FxShader189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minigame camera and fx shader on 1.8.9. renderWorldPass is where the camera shot swaps the render view (see CameraRender189);
 * getFOVModifier gives the shot's FOV; the held hand hides with the HUD; the fx shader runs right after renderWorld, on the main
 * framebuffer, before vanilla's own post effect (which is never touched). Every target is in vanilla's and OptiFine's EntityRenderer.
 */
@Mixin(EntityRenderer.class)
public abstract class MgEntityRendererMixin189 {
    @Shadow private Minecraft mc;

    @Inject(method = "renderWorldPass", at = @At("HEAD"))
    private void lads$shotBegin(int pass, float partial, long nano, CallbackInfo ci) {
        CameraRender189.begin(mc, partial);
    }

    @Inject(method = "renderWorldPass", at = @At("RETURN"))
    private void lads$shotEnd(int pass, float partial, long nano, CallbackInfo ci) {
        CameraRender189.end(mc);
    }

    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void lads$shotFov(float partial, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        CameraState.Pose p = CameraRender189.current();
        if (useFovSetting && p != null) cir.setReturnValue((float) p.fov());
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void lads$noHand(float partial, int pass, CallbackInfo ci) {
        if (Minigame.camera.hidesHud(Minigame.now())) ci.cancel();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void lads$fxShader(float partial, long nano, CallbackInfo ci) {
        FxShader189.run(mc, partial);
    }
}
