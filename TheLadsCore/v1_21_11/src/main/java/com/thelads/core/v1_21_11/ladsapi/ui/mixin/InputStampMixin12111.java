package com.thelads.core.v1_21_11.ladsapi.ui.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v1_21_11.ladsapi.ui.DialogScreen12111;
import com.thelads.core.v1_21_11.ladsapi.ui.Pad12111;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads API: "last device was the keyboard" stamp, and the number keys 1-6 choosing a button of an open vote or modal. */
@Mixin(KeyboardHandler.class)
public abstract class InputStampMixin12111 {
    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
    private void lads$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        Pad12111.kbm();
        if (action != InputConstants.PRESS) return;
        int k = event.key() - InputConstants.KEY_1;
        if (k < 0 || k > 5) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.screen != null && !(mc.screen instanceof DialogScreen12111))) return;
        if (DialogOverlay.choose(k)) ci.cancel();
    }
}
