package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyboardHandler.class)
public class NarratorShortcutMixin {
    @Redirect(method = "keyPress", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/GameNarrator;isActive()Z"), require = 1)
    private boolean lads$disableShortcut(GameNarrator narrator) {
        return !NativeQualityOfLife.enabled("DisableNarrator") && narrator.isActive();
    }
}
