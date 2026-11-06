package com.thelads.core.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_21_11.feature.NativeFeatures;
import com.thelads.core.v1_21_11.feature.NativeMenuKey;
import com.thelads.core.v1_21_11.gui.DraggableHudScreen12111;
import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
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
        if (window != Minecraft.getInstance().getWindow().handle()) return;
        if (NativeMenuKey.key(event, action)) { ci.cancel(); return; }
        NativeFeatures.key(event, action);
    }

    @WrapOperation(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;matches(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean ladsGuardFocusedTextInput(KeyMapping mapping, KeyEvent event, Operation<Boolean> original) {
        if (!isFunctionKey(event.key()) && isEditingText(Minecraft.getInstance().screen)) {
            return false;
        }
        return original.call(mapping, event);
    }

    @WrapOperation(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;isActive()Z"))
    private boolean ladsGuardNarratorWhileEditing(GameNarrator narrator, Operation<Boolean> original) {
        if (isEditingText(Minecraft.getInstance().screen)) return false;
        return original.call(narrator);
    }

    private static boolean isEditingText(Screen screen) {
        if (screen == null) return false;
        if (screen.getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) return true;
        var path = screen.getCurrentFocusPath();
        if (path != null && path.component() instanceof EditBox pathEdit && pathEdit.canConsumeInput()) return true;
        if (screen instanceof LadsSettingsScreen12111 ls) return ls.ui().isEditingText();
        return screen instanceof DraggableHudScreen12111 ds && ds.ui().isEditingText();
    }

    private static boolean isFunctionKey(int key) {
        return key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25;
    }
}
