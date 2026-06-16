/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelFuture
 *  net.minecraft.network.Connection
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.networking.avoid_deadlocks;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={Connection.class})
public class MixinClientConnection {
    @Shadow
    private Channel channel;
    @Unique
    private volatile boolean isClosing = false;

    @Redirect(method={"disconnect(Lnet/minecraft/network/DisconnectionDetails;)V"}, at=@At(value="INVOKE", target="Lio/netty/channel/ChannelFuture;awaitUninterruptibly()Lio/netty/channel/ChannelFuture;", remap=false))
    private ChannelFuture noDisconnectWait(ChannelFuture instance) {
        this.isClosing = true;
        return instance;
    }

    @Redirect(method={"*"}, at=@At(value="INVOKE", target="Lio/netty/channel/Channel;isOpen()Z", remap=false))
    private boolean redirectIsOpen(Channel instance) {
        return this.channel != null && this.channel.isOpen() && !this.isClosing;
    }
}

