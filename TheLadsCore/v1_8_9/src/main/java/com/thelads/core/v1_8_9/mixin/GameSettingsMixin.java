package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Zoom189;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lads Zoom is the only zoom: OptiFine's getFOVModifier asks GameSettings.isKeyDown(ofKeyBindZoom) each frame, so that key reads
 * as up while Lads Zoom is on. OptiFine then never divides the FOV or forces smooth camera; its binding and options are untouched.
 */
@Mixin(GameSettings.class)
public abstract class GameSettingsMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsOnlyZoom(KeyBinding key, CallbackInfoReturnable<Boolean> cir) {
        if (Zoom189.blocksOptiFine(key)) cir.setReturnValue(false);
    }
}
