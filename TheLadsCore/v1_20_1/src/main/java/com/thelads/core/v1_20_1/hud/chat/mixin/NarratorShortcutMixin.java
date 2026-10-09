package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.hud.Hud1201;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** DisableNarrator: Ctrl+B does not turn the narrator on. */
@Mixin(KeyboardHandler.class)
public abstract class NarratorShortcutMixin {
    @WrapOperation(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/GameNarrator;isActive()Z"), require = 1)
    private boolean lads$disableShortcut(GameNarrator narrator, Operation<Boolean> original) {
        return !Hud1201.enabled("DisableNarrator") && original.call(narrator);
    }
}
