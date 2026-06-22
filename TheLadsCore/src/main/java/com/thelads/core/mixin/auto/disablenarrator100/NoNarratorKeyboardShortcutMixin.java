package com.thelads.core.mixin.auto.disablenarrator100;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class NoNarratorKeyboardShortcutMixin {
    @Inject(method = "getNarrator", at = @At("HEAD"), require = 0, cancellable = true)
    private void disableNarratorKeyboardShortcut(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("");
    }
}
