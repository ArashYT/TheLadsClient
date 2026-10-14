package com.thelads.core.v26_2.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fast-closes LevelLoadingScreen (26.3 replacement for ReceivingLevelScreen) as soon as loadingPacketsReceived()
 * is signaled, without waiting for local chunk compilation or closeDelayMs on void lobbies/portals/servers.
 */
@Mixin(LevelLoadTracker.class)
public abstract class LevelLoadTrackerFastCloseMixin {
    @Unique
    private boolean lads$packetsReceived;

    @Inject(method = "startClientLoad", at = @At("HEAD"))
    private void lads$resetPacketsReceived(LocalPlayer player, ClientLevel level, CallbackInfo ci) {
        this.lads$packetsReceived = false;
    }

    @Inject(method = "loadingPacketsReceived", at = @At("RETURN"))
    private void lads$markPacketsReceived(CallbackInfo ci) {
        this.lads$packetsReceived = true;
    }

    @Inject(method = "isLevelReady", at = @At("HEAD"), cancellable = true)
    private void lads$readyOncePacketsReceived(CallbackInfoReturnable<Boolean> cir) {
        if (this.lads$packetsReceived) {
            cir.setReturnValue(true);
        }
    }
}
