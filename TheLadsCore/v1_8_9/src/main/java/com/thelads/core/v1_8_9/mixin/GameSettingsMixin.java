package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Zoom189;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads Zoom is the only zoom: OptiFine's getFOVModifier asks GameSettings.isKeyDown(ofKeyBindZoom) each frame, so that key reads
 * as up while Lads Zoom is on. OptiFine then never divides the FOV or forces smooth camera; its binding and options are untouched.
 * OptiFine's loadOfOptions (the end of every loadOptions) also unbinds every other key on its zoom key's code, Lads Zoom's C
 * included (KeyUtils.fixKeyConflicts): Lads Zoom is left out of that list. Without OptiFine the method is absent (require = 0).
 */
@Mixin(GameSettings.class)
public abstract class GameSettingsMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsOnlyZoom(KeyBinding key, CallbackInfoReturnable<Boolean> cir) {
        if (Zoom189.blocksOptiFine(key)) cir.setReturnValue(false);
    }

    @ModifyArg(method = "loadOfOptions", at = @At(value = "INVOKE", target = "Lnet/optifine/util/KeyUtils;fixKeyConflicts("
        + "[Lnet/minecraft/client/settings/KeyBinding;[Lnet/minecraft/client/settings/KeyBinding;)V"), index = 0, remap = false, require = 0)
    private KeyBinding[] ladsKeepZoomKey(KeyBinding[] keys) {
        return ArrayUtils.removeElement(keys, Zoom189.ZOOM);
    }
}
