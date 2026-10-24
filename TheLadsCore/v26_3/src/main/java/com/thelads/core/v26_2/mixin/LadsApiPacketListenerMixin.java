package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.v26_2.feature.LadsApiPayload;
import com.thelads.core.v26_2.feature.NativeLadsApi;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches incoming lads:api payloads on ClientPacketListener before vanilla drops them,
 * and notifies LadsApiClient of login, respawn, and configuration start events.
 */
@Mixin(ClientPacketListener.class)
abstract class LadsApiPacketListenerMixin {
    static {
        NativeLadsApi.initialize();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$onCustomPayload(CustomPacketPayload payload, CallbackInfo ci) {
        if (payload instanceof LadsApiPayload lads) {
            LadsApiClient.receive(lads.data());
            ci.cancel();
        }
    }

    @Inject(method = "handleLogin", at = @At("TAIL"), require = 1)
    private void lads$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        LadsApiClient.joinGame(packet.commonPlayerSpawnInfo().seed());
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"), require = 1)
    private void lads$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        LadsApiClient.respawn(packet.commonPlayerSpawnInfo().seed());
    }

    @Inject(method = "handleConfigurationStart", at = @At("HEAD"), require = 1)
    private void lads$onConfigurationStart(ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
        LadsApiClient.reset();
    }
}
