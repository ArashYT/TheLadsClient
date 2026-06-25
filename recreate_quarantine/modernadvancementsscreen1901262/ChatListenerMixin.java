package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatListenerMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), require = 0)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfo ci) {
        // Custom logic here
    }
}
