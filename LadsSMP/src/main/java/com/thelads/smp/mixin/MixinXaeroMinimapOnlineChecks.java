package com.thelads.smp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Same as {@link MixinXaeroOnlineChecks}, for Xaero's Minimap. */
@Pseudo
@Mixin(targets = "xaero.common.misc.Internet", remap = false)
public class MixinXaeroMinimapOnlineChecks {
    @Inject(method = "checkModVersion", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$noVersionCheck(CallbackInfo ci) {
        ci.cancel();
    }
}
