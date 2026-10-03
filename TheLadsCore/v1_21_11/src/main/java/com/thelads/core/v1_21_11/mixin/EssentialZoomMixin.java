package com.thelads.core.v1_21_11.mixin;
import com.thelads.core.v1_21_11.feature.NativeFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads Zoom is the only zoom: while it is on, Essential's zoom (its own key, also C) never starts, so its FOV change, smooth
 * camera and scroll capture stay off. Essential is downloaded at runtime: @Pseudo skips a missing class, require = 0 a changed method.
 */
@Pseudo
@Mixin(targets = "gg.essential.handlers.ZoomHandler", remap = false)
public abstract class EssentialZoomMixin {
    @Inject(method = "getZoomState", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ladsOnlyZoom(CallbackInfoReturnable<Boolean> cir) {
        if (NativeFeatures.enabled("Zoom")) cir.setReturnValue(false);
    }
}
