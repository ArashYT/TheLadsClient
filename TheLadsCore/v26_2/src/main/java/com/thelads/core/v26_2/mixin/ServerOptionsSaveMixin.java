package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeServerOptions;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** options.txt never gets a LadsClientAPI server's value: while it is written the option getters give the player's own (ServerOptionMixin). */
@Mixin(Options.class)
abstract class ServerOptionsSaveMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void lads$ownValues(CallbackInfo ci) {
        NativeServerOptions.saving = true;
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void lads$ownValuesDone(CallbackInfo ci) {
        NativeServerOptions.saving = false;
    }
}