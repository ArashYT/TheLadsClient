package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.gui.Screenshots12111;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenClickMixin12111 {
    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void ladsHandleScreenshotAction(ClickEvent clickEvent, Minecraft client, Screen screenAfterRun, CallbackInfo ci) {
        if (clickEvent instanceof Screenshots12111.ScreenshotChatActionClickEvent ae) {
            Screenshots12111.handleClick(ae);
            ci.cancel();
        }
    }
}
