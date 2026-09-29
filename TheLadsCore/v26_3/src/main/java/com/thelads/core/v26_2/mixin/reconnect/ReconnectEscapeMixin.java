package com.thelads.core.v26_2.mixin.reconnect;

import com.thelads.core.v26_2.feature.NativeReconnect;
import com.thelads.core.v26_2.feature.ReconnectDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ReconnectEscapeMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void lads$cancelFirstEscape(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ReconnectDialog dialog && dialog.lads$hasReconnectControls()
            && event.key() == 256 && NativeReconnect.cancelCountdown()) cir.setReturnValue(true);
    }
    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void lads$returnToParent(CallbackInfo ci) {
        if ((Object) this instanceof ReconnectDialog dialog && dialog.lads$hasReconnectControls()) {
            NativeReconnect.cancelAll(); Minecraft.getInstance().gui.setScreen(dialog.lads$reconnectParent()); ci.cancel();
        }
    }
}
