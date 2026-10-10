package com.thelads.core.v1_20_1.feature.fastquit.mixin;

import com.thelads.core.v1_20_1.feature.fastquit.FastQuit1201;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distant Horizons closes its singleplayer world off the render thread when leaving it (FastQuit1201.closeDhInBackground). */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
abstract class DhClientApiFastQuitMixin {
    @Inject(method = "onClientOnlyDisconnected", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$closeInBackground(CallbackInfo ci) {
        Object api = this;
        // The background thread calls the method again; off the render thread it runs as DH wrote it.
        if (FastQuit1201.closeDhInBackground(() -> {
            try {
                api.getClass().getMethod("onClientOnlyDisconnected").invoke(api);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        })) ci.cancel();
    }
}
