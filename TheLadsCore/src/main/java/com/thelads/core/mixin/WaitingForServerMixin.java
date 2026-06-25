package com.thelads.core.mixin;

import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.multiplayer.LevelLoadTracker$WaitingForServer")
public class WaitingForServerMixin {
    @Inject(method = "loadingPacketsReceived", at = @At("HEAD"), cancellable = true)
    private void onLoadingPacketsReceived(CallbackInfoReturnable<LevelLoadTracker.ClientLevelReady> cir) {
        cir.setReturnValue(new LevelLoadTracker.ClientLevelReady(Util.getMillis()));
    }
}
