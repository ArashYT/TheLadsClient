package com.thelads.core.mixin.auto.disablenarrator100;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.screens.Screen")
public class NoNarratorKeyboardShortcutMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), require = 0)
    private void disableNarratorShortcut(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == 61 || keyCode == 257) { // Example key codes for Narrator toggle
            cir.setReturnValue(true);
        }
    }
}
