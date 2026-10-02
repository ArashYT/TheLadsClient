package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Crosshair189;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crosshair Tweaks: F3's 3D crosshair (the world axes) follows Keep Vanilla Debug, Disable Crosshair and the visibility rules,
 * as 26.x NativeCrosshair.suppressDebugAxes. OptiFine patches EntityRenderer, so the hook is optional.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "renderWorldDirections", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsDebugAxes(float partialTicks, CallbackInfo ci) {
        if (Crosshair189.suppressDebugAxes()) ci.cancel();
    }
}
