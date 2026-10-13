package com.thelads.core.v1_8_9.mixin;

import java.util.List;
import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Shadow private static List<KeyBinding> keybindArray;
    @Shadow private void unpressKey() {}

    /** Replaces List.iterator() in unPressAllKeys with an indexed loop to avoid allocating an Iterator object. */
    @Inject(method = "unPressAllKeys", at = @At("HEAD"), cancellable = true, require = 1)
    private static void ladsUnPressAllKeys(CallbackInfo ci) {
        int len = keybindArray.size();
        for (int i = 0; i < len; i++) {
            ((KeyBindingMixin) (Object) keybindArray.get(i)).unpressKey();
        }
        ci.cancel();
    }
}
