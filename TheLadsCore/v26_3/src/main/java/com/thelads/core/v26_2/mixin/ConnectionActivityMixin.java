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
        if (receiving == PacketFlow.CLIENTBOUND) {
            lads$lastPacketNanos = System.nanoTime();
            if (com.thelads.core.client.rewind.InstantClipBuffer.isEnabled()) {
                try {
                    int packetId = 0;
                    byte[] data = null;
                    if (packet.type() != null && packet.type().id() != null) {
                        packetId = Math.abs(packet.type().id().hashCode()) & 0x7FFF;
                    }
                    try {
                        io.netty.buffer.ByteBuf buf = context.alloc().buffer();
                        net.minecraft.network.RegistryFriendlyByteBuf rfbb = new net.minecraft.network.RegistryFriendlyByteBuf(buf,
                            net.minecraft.client.Minecraft.getInstance().level != null ? net.minecraft.client.Minecraft.getInstance().level.registryAccess() : null);
                        net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.bind(b -> rfbb).codec().encode(rfbb, (Packet) packet);
                        data = new byte[rfbb.readableBytes()];
                        rfbb.readBytes(data);
                        rfbb.release();
                    } catch (Throwable fallback) {
                        if (packet.type() != null && packet.type().id() != null) {
                            data = packet.type().id().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        } else {
                            data = packet.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                    com.thelads.core.client.rewind.InstantClipBuffer.recordPacket(System.currentTimeMillis(), packetId, data);
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public long lads$lastPacketNanos() { return lads$lastPacketNanos; }
}