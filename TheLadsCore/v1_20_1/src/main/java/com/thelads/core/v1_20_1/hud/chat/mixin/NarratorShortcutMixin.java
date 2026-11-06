package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.gui.ScreenTextGuard1201;
import com.thelads.core.v1_20_1.hud.Hud1201;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** DisableNarrator: Ctrl+B does not turn the narrator on (nor while typing in a focused Lads text box). */
@Mixin(KeyboardHandler.class)
public abstract class NarratorShortcutMixin {
    @WrapOperation(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;isActive()Z"), require = 1)
    private boolean lads$disableShortcut(GameNarrator narrator, Operation<Boolean> original) {
        return !Hud1201.enabled("DisableNarrator")
            && !ScreenTextGuard1201.isEditingText(Minecraft.getInstance().screen)
            && original.call(narrator);
    }
}
