package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeKillBanner;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class KillBannerStatsMixin {
    @Inject(method = "handleAwardStats", at = @At("TAIL"), require = 1)
    private void lads$serverPlayerKills(ClientboundAwardStatsPacket packet, CallbackInfo callback) {
        NativeKillBanner.receivedStats((ClientPacketListener) (Object) this);
    }
}
