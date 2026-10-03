package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Crosshair189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.Zoom189;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crosshair Tweaks: F3's 3D crosshair (the world axes) follows Keep Vanilla Debug, Disable Crosshair and the visibility rules,
 * as 26.x NativeCrosshair.suppressDebugAxes. OptiFine patches EntityRenderer, so the hook is optional.
 * 1.7 Animations, Instant sneak camera: orientCamera's one eye-height read (one in OptiFine M5's orientCamera too) becomes 1.7's.
 * Zoom: getFOVModifier (kept by OptiFine M5) says whether the FOV Forge's FOVModifier event is about to get is the held item's.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "renderWorldDirections", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsDebugAxes(float partialTicks, CallbackInfo ci) {
        if (Crosshair189.suppressDebugAxes()) ci.cancel();
    }

    @Inject(method = "getFOVModifier", at = @At("HEAD"), require = 1)
    private void ladsZoomPass(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        Zoom189.handPass = !useFOVSetting;
    }

    @Redirect(method = "orientCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getEyeHeight()F"), require = 1, allow = 1)
    private float ladsEyeHeight(Entity entity, float partialTicks) {
        return OldAnimations189.cameraEyeHeight(entity, partialTicks);
    }
}
