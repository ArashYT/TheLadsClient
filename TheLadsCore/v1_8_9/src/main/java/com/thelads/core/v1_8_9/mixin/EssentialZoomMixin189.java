package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads Zoom is the only zoom: while it is on, Essential's zoom (its own key, also C) never starts. Essential already stands down
 * whenever OptiFine is loaded, as it is in the 1.8.9 pack; this covers a game without OptiFine. Essential is downloaded at
 * runtime: @Pseudo skips a missing class, require = 0 a changed method.
 */
@Pseudo
@Mixin(targets = "gg.essential.handlers.ZoomHandler", remap = false)
public abstract class EssentialZoomMixin189 {
    @Inject(method = "getZoomState", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ladsOnlyZoom(CallbackInfoReturnable<Boolean> cir) {
        Module zoom = ModuleManager.getInstance().getModule("Zoom");
        if (zoom != null && zoom.isEnabled()) cir.setReturnValue(false);
    }
}
