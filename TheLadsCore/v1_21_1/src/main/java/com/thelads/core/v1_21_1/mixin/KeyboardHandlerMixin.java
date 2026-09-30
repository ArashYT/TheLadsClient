package com.thelads.core.v1_21_1.mixin;
import com.thelads.core.v1_21_1.feature.NativeFeatures;
import com.thelads.core.v1_21_1.feature.NativeMenuKey;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        if (NativeMenuKey.key(key, scancode, action, modifiers)) { ci.cancel(); return; }
        NativeFeatures.key(key, scancode, action);
    }
}
