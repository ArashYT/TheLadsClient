package com.thelads.core.v1_20_1.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import com.thelads.core.v1_20_1.feature.NativeMenuKey1201;
import com.thelads.core.v1_20_1.gui.ScreenTextGuard1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        if (NativeMenuKey1201.key(key, scancode, action, modifiers)) {
            ci.cancel();
            return;
        }
        NativeFeatures1201.key(key, scancode, action, modifiers);
    }

    @WrapOperation(method = "keyPress(JIIII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;matches(II)Z"))
    private boolean ladsGuardFocusedTextInput(KeyMapping mapping, int key, int scancode, Operation<Boolean> original) {
        if (!isFunctionKey(key) && ScreenTextGuard1201.isEditingText(Minecraft.getInstance().screen)) {
            return false;
        }
        return original.call(mapping, key, scancode);
    }

    private static boolean isFunctionKey(int key) {
        return key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25;
    }
}
