package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.PacketErrorPolicy;
import com.thelads.core.v1_8_9.feature.ConnectionTweaks189;
import com.thelads.core.v1_8_9.feature.SignalLoss189;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
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
 * Real ping (Hypixel189): the hypixel:ping answer's arrival.
 * IgnorePacketErrors: a play packet whose handler throws on the network thread is logged and skipped instead of disconnecting
 * ("Internal Exception"). Handlers that run on the client thread only log their errors in 1.8.9 anyway.
 */
@Mixin(NetworkManager.class)
public abstract class NetworkManagerMixin implements SignalLoss189.PacketActivity {
    @Shadow @Final private EnumPacketDirection direction;
    @Unique private volatile long ladsLastPacketNanos;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), require = 1)
    private void ladsReceived(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (direction != EnumPacketDirection.CLIENTBOUND) return;
        ladsLastPacketNanos = System.nanoTime();
        // 1.9.1 real ping: the Hypixel Mod API's answer as it arrives (Hypixel189).
        // lads:api transport: payload delivered to LadsApiClient (LadsApi189).
        if (packet instanceof S3FPacketCustomPayload) {
            S3FPacketCustomPayload customPayload = (S3FPacketCustomPayload) packet;
            com.thelads.core.v1_8_9.feature.Hypixel189.payload(customPayload);
            com.thelads.core.v1_8_9.feature.LadsApi189.receive(customPayload);
        }
        if (packet instanceof net.minecraft.network.play.server.S24PacketBlockAction) com.thelads.core.v1_8_9.feature.Hypixel189.blockAction((net.minecraft.network.play.server.S24PacketBlockAction) packet);
    }

    /** Cancel pressed while the socket was still opening (ConnectionTweaks189.cancelledConnect): close it before it logs in. */
    @Inject(method = "setNetHandler", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsCancelledConnect(INetHandler handler, CallbackInfo ci) {
        if (handler instanceof net.minecraft.client.network.NetHandlerLoginClient && ConnectionTweaks189.cancelledConnect((NetworkManager) (Object) this)) {
            ((NetworkManager) (Object) this).closeChannel(new net.minecraft.util.ChatComponentTranslation("connect.aborted"));
            ci.cancel();
        }
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
            PacketErrorPolicy.skipped("play packet " + packet.getClass().getSimpleName() + ", whose handler failed,", failure);
        }
    }

    @Override
    public long ladsLastPacketNanos() { return ladsLastPacketNanos; }

    /** Fast multiplayer disconnect: allows Netty to close the channel asynchronously without freezing the UI thread. */
    @Redirect(method = "closeChannel", at = @At(value = "INVOKE",
        target = "Lio/netty/channel/ChannelFuture;awaitUninterruptibly()Lio/netty/channel/ChannelFuture;", remap = false), require = 1)
    private ChannelFuture ladsAsyncClose(ChannelFuture future) {
        return future;
    }
}
