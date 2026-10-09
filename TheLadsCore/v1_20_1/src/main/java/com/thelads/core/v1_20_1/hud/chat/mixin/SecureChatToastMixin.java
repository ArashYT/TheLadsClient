package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.thelads.core.v1_20_1.hud.chat.ChatFeatures1201;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Signing Indicators also drops the "Chat messages can not be verified" toast. Other toasts are untouched. */
@Mixin(ToastComponent.class)
public abstract class SecureChatToastMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$dropUnverifiedChatToast(Toast toast, CallbackInfo ci) {
        if (toast instanceof SystemToast system && system.getToken() == SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING
            && ChatFeatures1201.hideChatSigning()) ci.cancel();
    }
}
