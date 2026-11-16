package com.thelads.smp.qa.mixin;

import com.thelads.smp.qa.FrameWatch;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: logs every chunk batch the server sends, with the state of its connection (ladsqa/chunkack.txt). */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class MixinServerChunkAckDebug {
    @Shadow @org.spongepowered.asm.mixin.Final protected Connection connection;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void ladsqa$send(Packet<?> p, CallbackInfo ci) {
        if (!FrameWatch.ON) return;
        if (p instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket b) {
            boolean odd = false;
            StringBuilder kinds = new StringBuilder();
            int n = 0;
            for (Packet<?> sp : b.subPackets()) {
                n++;
                if (sp instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket || sp instanceof net.minecraft.network.protocol.BundleDelimiterPacket<?>) odd = true;
                if (n <= 3) kinds.append(sp.getClass().getSimpleName()).append(' ');
            }
            if (odd) FrameWatch.ack("SERVER send ODD BUNDLE (nested bundle or delimiter inside) n=" + n + " first=" + kinds + " thread=" + Thread.currentThread().getName());
        } else if (p instanceof net.minecraft.network.protocol.BundleDelimiterPacket<?>) {
            FrameWatch.ack("SERVER send raw BundleDelimiterPacket thread=" + Thread.currentThread().getName());
        }
        if (p instanceof ClientboundChunkBatchStartPacket || p instanceof ClientboundChunkBatchFinishedPacket) {
            String st;
            try {
                java.lang.reflect.Field f = Connection.class.getDeclaredField("channel");
                f.setAccessible(true);
                io.netty.channel.Channel ch = (io.netty.channel.Channel) f.get(connection);
                st = " connected=" + connection.isConnected() + " active=" + ch.isActive() + " writable=" + ch.isWritable();
            } catch (Throwable t) { st = " stateFail=" + t; }
            FrameWatch.ack("SERVER send " + (p instanceof ClientboundChunkBatchStartPacket ? "batchStart" : "batchFinished size=" + ((ClientboundChunkBatchFinishedPacket) p).batchSize()) + st);
        }
    }
}
