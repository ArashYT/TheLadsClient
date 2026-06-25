package com.thelads.core.mixin.auto.disablenarrator100;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager")
public class NoNarratorToastMixin {

    @Inject(method = "add", at = @At("HEAD"), require = 0)
    private void disableNarratorToast(net.minecraft.client.gui.components.toasts.Toast toast, CallbackInfoReturnable<Void> cir) {
        if (toast instanceof net.minecraft.client.gui.components.toasts.NarrationToast) {
            cir.setReturnValue(null);
        }
    }
}
