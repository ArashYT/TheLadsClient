package com.thelads.core.mixin.auto.disablenarrator100;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.NarratorManager$NarratorLinux")
public class NoLinuxNarratorMixin {

    @Inject(method = "narrate", at = @At("HEAD"), require = 0)
    private void disableNarration(String message, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
