package com.thelads.core.v1_8_9.mixin.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero's Minimap (the launcher's 1.8.9 pack, Minimap189) asks data.chocolateminecraft.com for its Patreon list and newest version
 * on the game thread while the game starts, with 900 ms timeouts that its server (about 940 ms to answer) always misses: about 2 s
 * of every 1.8.9 start for nothing. The launcher pins the version, so both are skipped (XaeroVersionCheckMixin189: the version).
 * One target per mixin: 1.8.9's Mixin 0.7.11 fails the whole class when an injector finds no method in one of several targets.
 */
@Pseudo
@Mixin(targets = "xaero.patreon.Patreon7", remap = false)
public abstract class XaeroOnlineChecksMixin189 {
    @Inject(method = "checkPatreon", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void ladsSkipPatreon(CallbackInfo ci) { ci.cancel(); }
}
