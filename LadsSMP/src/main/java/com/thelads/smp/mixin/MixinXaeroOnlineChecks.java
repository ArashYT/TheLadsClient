package com.thelads.smp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero's World Map downloads its update list on the render thread while the loading screen is up (a network round trip with a
 * 900 ms timeout: the window stayed on one frame for up to 3.5 s). The launcher updates the pack, so the update notice is useless here.
 */
@Pseudo
@Mixin(targets = "xaero.map.misc.Internet", remap = false)
public class MixinXaeroOnlineChecks {
    @Inject(method = "checkModVersion", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$noVersionCheck(CallbackInfo ci) {
        ci.cancel();
    }
}
