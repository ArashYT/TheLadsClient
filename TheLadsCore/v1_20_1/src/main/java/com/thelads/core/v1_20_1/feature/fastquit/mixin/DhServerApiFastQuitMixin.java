package com.thelads.core.v1_20_1.feature.fastquit.mixin;

import com.thelads.core.v1_20_1.feature.fastquit.FastQuit1201;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distant Horizons' next world never starts while the last one is still closing in the background. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ServerApi", remap = false)
abstract class DhServerApiFastQuitMixin {
    @Inject(method = "serverLoadEvent", at = @At("HEAD"), remap = false, require = 0)
    private void lads$afterLastClose(CallbackInfo ci) {
        FastQuit1201.awaitDhClose();
    }
}
