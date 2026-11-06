package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v26_2.feature.NativeFeatures;
import com.thelads.core.v26_2.feature.NativeMenuKey;
import com.thelads.core.v26_2.gui.ScreenTextGuard26;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (Boolean.getBoolean("thelads.verifyInput") && (event.key() == 340 || event.key() == 344))
            org.slf4j.LoggerFactory.getLogger("TheLadsCore-Input").info(
                "Shift input: key={} scan={} action={} active={} bound={}", event.key(), event.scancode(), action,
                Minecraft.getInstance().isWindowActive(), com.thelads.core.v26_2.feature.NativeKeyBindings.MODULES.matches(event));
        if (window != Minecraft.getInstance().getWindow().handle()) return;
        if (NativeMenuKey.key(event, action)) { ci.cancel(); return; }
        NativeFeatures.key(event, action);
    }

    @WrapOperation(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleGlobalKeyPress(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)Z"))
    private boolean ladsGuardGlobalKeysWhileEditing(Minecraft mc, InputConstants.Key key, boolean inGame, Operation<Boolean> original) {
        Screen screen = mc.gui != null ? mc.gui.screen() : null;
        if (key.getType() == InputConstants.Type.KEYSYM && !isFunctionKey(key.getValue()) && ScreenTextGuard26.isEditingText(screen)) {
            return false;
        }
        return original.call(mc, key, inGame);
    }

    private static boolean isFunctionKey(int key) {
        return key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25;
    }
}
