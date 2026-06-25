package com.thelads.core.mixin;

import com.thelads.core.modules.PingViewModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.client.benchmark.BenchmarkTracker;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handlePongResponse", at = @At("HEAD"), require = 0)
    private void onPongResponse(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PingViewModule.onPong(packet.time());
    }

    @Inject(method = "startWaitingForNewLevel", at = @At("HEAD"))
    private void onStartWaitingForNewLevel(CallbackInfo ci) {
        BenchmarkTracker.setJoinStartTime(System.nanoTime());
    }

    @Inject(method = "notifyPlayerLoaded", at = @At("HEAD"))
    private void onNotifyPlayerLoaded(CallbackInfo ci) {
        BenchmarkTracker.setJoinEndTime(System.nanoTime());
    }
}
