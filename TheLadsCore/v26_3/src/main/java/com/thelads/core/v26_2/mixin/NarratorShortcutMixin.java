package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardHandler.class)
public class NarratorShortcutMixin {
    @Redirect(method = "keyPress", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/GameNarrator;isActive()Z"), require = 1)
    private boolean lads$disableShortcut(GameNarrator narrator) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui != null ? mc.gui.screen() : null;
        return !NativeQualityOfLife.enabled("DisableNarrator")
            && (screen == null || !screen.isInputCaptured())
            && narrator.isActive();
    }
}
