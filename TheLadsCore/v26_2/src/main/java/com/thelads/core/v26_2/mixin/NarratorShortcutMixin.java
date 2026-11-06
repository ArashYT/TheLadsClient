package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import com.thelads.core.v26_2.gui.ScreenTextGuard26;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardHandler.class)
public class NarratorShortcutMixin {
    @Redirect(method = "keyPress", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/GameNarrator;isActive()Z"), require = 1)
    private boolean lads$disableShortcut(GameNarrator narrator) {
        Minecraft mc = Minecraft.getInstance();
        return !NativeQualityOfLife.enabled("DisableNarrator")
            && !ScreenTextGuard26.isEditingText(mc.gui != null ? mc.gui.screen() : null)
            && narrator.isActive();
    }
}
