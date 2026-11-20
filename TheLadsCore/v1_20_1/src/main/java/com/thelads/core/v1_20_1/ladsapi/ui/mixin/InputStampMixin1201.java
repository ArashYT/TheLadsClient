package com.thelads.core.v1_20_1.ladsapi.ui.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v1_20_1.ladsapi.ui.DialogScreen1201;
import com.thelads.core.v1_20_1.ladsapi.ui.Pad1201;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads API: "last device was the keyboard" stamp, and the number keys 1-6 choosing a button of an open vote or modal. */
@Mixin(KeyboardHandler.class)
public abstract class InputStampMixin1201 {
    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"), cancellable = true)
    private void lads$key(long window, int key, int scanCode, int action, int mods, CallbackInfo ci) {
        Pad1201.kbm();
        if (action != InputConstants.PRESS) return;
        int k = key - InputConstants.KEY_1;
        if (k < 0 || k > 5) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.screen != null && !(mc.screen instanceof DialogScreen1201))) return;
        if (DialogOverlay.choose(k)) ci.cancel();
    }
}
