package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ConnectionTweaks;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Signing Indicators also drops the "Chat messages can't be verified" toast. Other toasts are untouched. */
@Mixin(ToastManager.class)
abstract class SecureChatToastMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$dropUnverifiedChatToast(Toast toast, CallbackInfo ci) {
        if (toast instanceof SystemToast system && system.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING
            && ConnectionTweaks.hideChatSigning()) ci.cancel();
    }
}
