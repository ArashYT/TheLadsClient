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

    @Override
    public long lads$lastPacketNanos() { return lads$lastPacketNanos; }
}
