package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.v1_8_9.feature.ConnectionTweaks189;
import com.thelads.core.v1_8_9.feature.SignalLoss189;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SignalLoss (SignalLoss189), as 26.x ConnectionActivityMixin: each connection's last received server packet, on the network thread.
 * IgnorePacketErrors: a play packet whose handler throws on the network thread is logged and skipped instead of disconnecting
 * ("Internal Exception"). Handlers that run on the client thread only log their errors in 1.8.9 anyway.
 */
@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin implements SignalLoss189.PacketActivity {
    @Shadow @Final private EnumPacketDirection direction;
    @Unique private volatile long ladsLastPacketNanos;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), require = 1)
    private void ladsReceived(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (direction == EnumPacketDirection.CLIENTBOUND) ladsLastPacketNanos = System.nanoTime();
    }

    @Redirect(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", require = 1,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Packet;processPacket(Lnet/minecraft/network/INetHandler;)V"))
    private void ladsHandle(Packet<INetHandler> packet, INetHandler handler) {
        try {
            packet.processPacket(handler);
        } catch (ThreadQuickExitException scheduled) {
            throw scheduled;
        } catch (RuntimeException failure) {
            if (!(handler instanceof NetHandlerPlayClient) || !ConnectionTweaks189.ignorePacketErrors() || !PacketErrorPolicy.skippable(failure)) throw failure;
            PacketErrorPolicy.skipped("play packet that failed to apply:", packet.getClass().getSimpleName(), failure);
        }
    }

    @Override
    public long ladsLastPacketNanos() { return ladsLastPacketNanos; }
}
