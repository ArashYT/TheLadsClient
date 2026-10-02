package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.SignalLoss189;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SignalLoss (SignalLoss189), as 26.x ConnectionActivityMixin: each connection's last received server packet, on the network thread. */
@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin implements SignalLoss189.PacketActivity {
    @Shadow @Final private EnumPacketDirection direction;
    @Unique private volatile long ladsLastPacketNanos;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), require = 1)
    private void ladsReceived(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (direction == EnumPacketDirection.CLIENTBOUND) ladsLastPacketNanos = System.nanoTime();
    }

    @Override
    public long ladsLastPacketNanos() { return ladsLastPacketNanos; }
}
