package com.thelads.core.v1_20_1.mixin;

import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fast-closes ReceivingLevelScreen as soon as the server's position/loading packets have been received,
 * avoiding waiting for local chunk meshing or extra ticks when joining void lobbies, servers, or portals.
 */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenFastCloseMixin {
    @Shadow
    private boolean loadingPacketsReceived;

    @Shadow
    public abstract void onClose();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void lads$fastCloseWhenPacketsReceived(CallbackInfo ci) {
        if (this.loadingPacketsReceived) {
            this.onClose();
            ci.cancel();
        }
    }
}
