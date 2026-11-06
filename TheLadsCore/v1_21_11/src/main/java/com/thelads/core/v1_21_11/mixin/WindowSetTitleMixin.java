package com.thelads.core.v1_21_11.mixin;

import com.mojang.blaze3d.platform.Window;
import com.thelads.core.client.WindowTitle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The window title stays The Lads Client's: another mod's setTitle (Modpack Core Essentials does it every tick) is dropped. */
@Mixin(Window.class)
public abstract class WindowSetTitleMixin {
    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$ownTitle(String title, CallbackInfo ci) {
        if (WindowTitle.foreign()) ci.cancel();
    }
}
