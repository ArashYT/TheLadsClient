package com.thelads.core.mixin.auto.decentscreenshot10262;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private void onInit(CallbackInfo ci) {
        // Minimal safe injection to demonstrate the mixin structure
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;II)V"), require = 0)
    private void onRender(GuiGraphics graphics, Component component, int x, int y, CallbackInfo ci) {
        // Minimal safe injection to demonstrate the mixin structure
    }
}
