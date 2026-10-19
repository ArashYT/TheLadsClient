package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v26_2.showcase.ShowcaseDirector26;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Showcase director: the recording game never grabs or warps the OS mouse (it would yank the owner's cursor to an off-screen window). */
@Mixin(InputConstants.class)
public abstract class ShowcaseMouseMixin {
    @Inject(method = {"grabMouse", "releaseMouse"}, at = @At("HEAD"), cancellable = true)
    private static void lads$keepRealMouse(CallbackInfo ci) {
        if (ShowcaseDirector26.isActive()) ci.cancel();
    }
}
