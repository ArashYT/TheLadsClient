package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.PacketActivitySource;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionActivityMixin implements PacketActivitySource {
    @Shadow @Final private PacketFlow receiving;
    @Unique private volatile long lads$lastPacketNanos;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD"), require = 1)
    private void lads$received(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        if (receiving == PacketFlow.CLIENTBOUND) lads$lastPacketNanos = System.nanoTime();
    }

    /** Connection Tuning: nodelay, keepalive and low-delay TOS on the client's TCP socket. */
    @Inject(method = "channelActive", at = @At("HEAD"))
    private void lads$tuneSocket(ChannelHandlerContext context, CallbackInfo callback) {
        io.netty.channel.Channel channel = context.channel();
        if (receiving != PacketFlow.CLIENTBOUND || channel instanceof io.netty.channel.local.LocalChannel) return;
        if (!com.thelads.core.perf.ConnectionTuning.shouldApply()) return;
        try {
            channel.config().setOption(io.netty.channel.ChannelOption.TCP_NODELAY, true);
            channel.config().setOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true);
            channel.config().setOption(io.netty.channel.ChannelOption.IP_TOS, com.thelads.core.perf.ConnectionTuning.TOS_LOW_DELAY);
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] Connection Tuning applied to {}", channel.remoteAddress());
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("[Lads] Connection Tuning skipped: {}", e.toString());
        }
    }

    /** Packet Flush Batching: one socket flush per event-loop pass on the client channel (not LocalChannel; krypton does it itself). */
    @Inject(method = "channelActive", at = @At("HEAD"))
    private void lads$flushBatching(ChannelHandlerContext context, CallbackInfo callback) {
        io.netty.channel.Channel channel = context.channel();
        if (receiving != PacketFlow.CLIENTBOUND || channel instanceof io.netty.channel.local.LocalChannel) return;
        if (!com.thelads.core.perf.FlushBatching.shouldInstall(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("krypton"))) return;
        if (channel.pipeline().get("lads_flush_batch") != null) return;
        channel.pipeline().addFirst("lads_flush_batch",
            new io.netty.handler.flush.FlushConsolidationHandler(com.thelads.core.perf.FlushBatching.EXPLICIT_FLUSH_AFTER, true));
    }

    @Override
    public long lads$lastPacketNanos() { return lads$lastPacketNanos; }
}
