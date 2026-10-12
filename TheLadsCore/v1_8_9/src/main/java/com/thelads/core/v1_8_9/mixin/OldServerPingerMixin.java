package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OldServerPinger.class)
public abstract class OldServerPingerMixin {
    @Inject(method = "tryCompatibilityPing", at = @At("HEAD"), cancellable = true)
    private void ladsSuppressLegacyPing(ServerData server, CallbackInfo ci) {
        ci.cancel();
    }
}
