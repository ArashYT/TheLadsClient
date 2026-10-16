package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.NarratorStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameNarrator.class)
public class NarratorMixin {
    // WrapOperation, not Redirect: ModernFix wraps the same call, and two Redirects on one call conflict.
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"), require = 1)
    private com.mojang.text2speech.Narrator lads$lazyPlatformNarrator(Operation<com.mojang.text2speech.Narrator> original) {
        return com.thelads.core.v26_2.feature.NativeNarrator.create();
    }

    @Inject(method = "checkStatus", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$disabledIsIntentional(boolean requireNarrator, CallbackInfo callback) {
        if (NativeQualityOfLife.enabled("DisableNarrator")) callback.cancel();
    }

    @Inject(method = "narrateMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$silence(String text, boolean interrupt, CallbackInfo callback) {
        if (NativeQualityOfLife.enabled("DisableNarrator")) callback.cancel();
    }

    @Inject(method = "updateNarratorStatus", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$suppressToggleToast(NarratorStatus status, CallbackInfo callback) {
        if (NativeQualityOfLife.enabled("DisableNarrator")) {
            ((GameNarrator) (Object) this).clear();
            callback.cancel();
        }
    }
}
