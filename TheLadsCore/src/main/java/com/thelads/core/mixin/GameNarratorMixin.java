package com.thelads.core.mixin;

import net.minecraft.client.GameNarrator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameNarrator.class)
public class GameNarratorMixin {

    @Inject(method = "say", at = @At("HEAD"), cancellable = true, require = 0)
    private void thelads$cancelSay(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "sayNow", at = @At("HEAD"), cancellable = true, require = 0)
    private void thelads$cancelSayNow(CallbackInfo ci) {
        ci.cancel();
    }
}
