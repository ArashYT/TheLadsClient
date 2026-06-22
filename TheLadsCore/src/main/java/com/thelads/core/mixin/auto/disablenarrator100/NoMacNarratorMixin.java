package com.thelads.core.mixin.auto.disablenarrator100;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class NoMacNarratorMixin {
    @Inject(method = "getNarrator", at = @At("HEAD"), require = 0, cancellable = true)
    private void disableMacNarrator(CallbackInfoReturnable<String> cir) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            cir.setReturnValue("");
        }
    }
}
