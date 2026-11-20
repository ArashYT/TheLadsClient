package com.thelads.core.v1_8_9.ladsapi.ui.mixin;

import com.thelads.core.v1_8_9.ladsapi.ui.ServerKeys189;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server action keys are temporary: options.txt is written without them (the array is swapped for the duration of saveOptions). */
@Mixin(GameSettings.class)
public abstract class GameSettingsServerKeysMixin189 {
    @Shadow public KeyBinding[] keyBindings;
    private KeyBinding[] ladsAllKeys;

    @Inject(method = "saveOptions", at = @At("HEAD"))
    private void lads$hideServerKeys(CallbackInfo ci) {
        if (!ServerKeys189.any(keyBindings)) return;
        ladsAllKeys = keyBindings;
        keyBindings = ServerKeys189.without(keyBindings);
    }

    @Inject(method = "saveOptions", at = @At("RETURN"))
    private void lads$restoreKeys(CallbackInfo ci) {
        if (ladsAllKeys != null) keyBindings = ladsAllKeys;
        ladsAllKeys = null;
    }
}
