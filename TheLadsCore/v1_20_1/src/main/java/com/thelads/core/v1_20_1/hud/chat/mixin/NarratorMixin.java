package com.thelads.core.v1_20_1.hud.chat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.text2speech.Narrator;
import com.thelads.core.v1_20_1.hud.Hud1201;
import com.thelads.core.v1_20_1.hud.chat.NativeNarrator1201;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.NarratorStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DisableNarrator: no platform speech library, no start-up dialog, no toggle toast while the module is on. */
@Mixin(GameNarrator.class)
public abstract class NarratorMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"), require = 1)
    private Narrator lads$lazyPlatformNarrator(Operation<Narrator> original) {
        return NativeNarrator1201.create();
    }

    @Inject(method = "checkStatus", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$disabledIsIntentional(boolean requireNarrator, CallbackInfo callback) {
        if (Hud1201.enabled("DisableNarrator")) callback.cancel();
    }

    @Inject(method = "updateNarratorStatus", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$suppressToggleToast(NarratorStatus status, CallbackInfo callback) {
        if (Hud1201.enabled("DisableNarrator")) {
            ((GameNarrator) (Object) this).clear();
            callback.cancel();
        }
    }
}
