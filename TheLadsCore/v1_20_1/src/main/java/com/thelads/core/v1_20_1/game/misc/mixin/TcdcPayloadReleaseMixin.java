package com.thelads.core.v1_20_1.game.misc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TCDCommons 3.12.3.3 (Zero SMP, bundled by Better Stats) overrides {@code finalize()} on {@code TCustomPayload} to call
 * {@code release()} on its {@code FriendlyByteBuf}. Forge's network pipeline already releases the buffer when the packet is
 * handled, so when GC later runs the finalizer on the Finalizer thread it frees a buffer whose refCnt is already 0 and logs
 * an {@code IllegalReferenceCountException: refCnt: 0, decrement: 1} stack trace. Without TCDCommons: nothing.
 */
@Pseudo
@Mixin(targets = "io.github.thecsdev.tcdcommons.api.network.packet.TCustomPayload", remap = false)
public abstract class TcdcPayloadReleaseMixin {
    @SuppressWarnings("deprecation")
    @Inject(method = "finalize()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lads$skipFinalizerRelease(CallbackInfo ci) {
        ci.cancel();
    }
}
