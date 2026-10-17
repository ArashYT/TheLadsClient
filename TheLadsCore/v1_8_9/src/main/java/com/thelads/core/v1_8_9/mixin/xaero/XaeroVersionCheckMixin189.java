package com.thelads.core.v1_8_9.mixin.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Xaero's Minimap's start-up version check, skipped as its Patreon check (XaeroOnlineChecksMixin189). */
@Pseudo
@Mixin(targets = "xaero.common.misc.Internet", remap = false)
public abstract class XaeroVersionCheckMixin189 {
    @Inject(method = "checkModVersion", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ladsSkipVersionCheck(CallbackInfo ci) { ci.cancel(); }
}
