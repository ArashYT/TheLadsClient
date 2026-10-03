package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.NativeFeatures;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads Zoom is the only zoom: while it is on, Essential's zoom key ("keybind.name.ZOOM", also C) reads as up, so Essential's
 * ZoomHandler never starts its FOV change, smooth camera or scroll capture. Essential is downloaded at runtime and its classes load
 * after mixins are prepared, so this sits on the vanilla KeyMapping it polls; its binding and options are untouched.
 */
@Mixin(KeyMapping.class)
public abstract class EssentialZoomMixin {
    @Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsOnlyZoom(CallbackInfoReturnable<Boolean> cir) {
        if ("keybind.name.ZOOM".equals(((KeyMapping) (Object) this).getName()) && NativeFeatures.enabled("Zoom")) cir.setReturnValue(false);
    }
}
