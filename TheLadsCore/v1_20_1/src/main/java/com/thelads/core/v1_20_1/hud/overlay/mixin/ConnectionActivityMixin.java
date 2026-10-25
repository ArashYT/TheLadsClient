package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.thelads.core.v1_20_1.hud.overlay.PacketActivity1201;
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

/** SignalLoss: when the client's connection last received a packet (netty thread; read on the render thread). */
@Mixin(Connection.class)
public class ConnectionActivityMixin implements PacketActivity1201 {
    @Shadow @Final private PacketFlow receiving;
    @Unique private volatile long lads$lastPacketNanos;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void lads$received(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        if (receiving == PacketFlow.CLIENTBOUND) {
            lads$lastPacketNanos = System.nanoTime();
            if (com.thelads.core.client.rewind.InstantClipBuffer.isEnabled()) {
                try {
                    io.netty.buffer.ByteBuf bb = io.netty.buffer.Unpooled.buffer();
                    net.minecraft.network.FriendlyByteBuf fbb = new net.minecraft.network.FriendlyByteBuf(bb);
                    packet.write(fbb);
                    byte[] data = new byte[fbb.readableBytes()];
                    fbb.readBytes(data);
                    fbb.release();
                    int packetId = 0;
                    try {
                        Integer id = net.minecraft.network.ConnectionProtocol.PLAY.getPacketId(receiving, packet);
                        if (id != null) packetId = id;
                    } catch (Throwable ignored) {}
                    com.thelads.core.client.rewind.InstantClipBuffer.recordPacket(System.currentTimeMillis(), packetId, data);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public long lads$lastPacketNanos() { return lads$lastPacketNanos; }
}
