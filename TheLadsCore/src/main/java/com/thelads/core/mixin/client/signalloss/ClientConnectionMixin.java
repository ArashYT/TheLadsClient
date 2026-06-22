/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.ChannelHandlerContext
 *  net.minecraft.network.Connection
 *  net.minecraft.network.protocol.Packet
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.client.signalloss;

import com.thelads.core.features.signalloss.SignalLossClient;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Connection.class})
public class ClientConnectionMixin {
    @Inject(method={"channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V"}, at={@At(value="HEAD")})
    private void onPacketReceived(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo ci) {
        SignalLossClient.lastPacketTime = System.nanoTime();
    }
}

