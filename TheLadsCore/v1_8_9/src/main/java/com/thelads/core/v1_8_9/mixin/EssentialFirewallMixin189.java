package com.thelads.core.v1_8_9.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "gg.essential.util.FirewallUtil", remap = false)
public abstract class EssentialFirewallMixin189 {
    @Inject(method = "isFirewallBlocking", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ladsNoFirewallBlocking(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
