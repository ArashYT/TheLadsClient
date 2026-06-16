/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelFutureListener
 *  io.netty.channel.EventLoop
 *  io.netty.util.concurrent.AbstractEventExecutor
 *  net.minecraft.network.Connection
 *  net.minecraft.network.protocol.Packet
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.networking.no_flush;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.AbstractEventExecutor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={Connection.class})
public class MixinClientConnection {
    @Redirect(method={"tick"}, at=@At(value="INVOKE", target="Lio/netty/channel/Channel;flush()Lio/netty/channel/Channel;"))
    private Channel dontFlush(Channel instance) {
        return instance;
    }

    @WrapOperation(method={"sendPacket"}, at={@At(value="INVOKE", target="Lio/netty/channel/EventLoop;execute(Ljava/lang/Runnable;)V")})
    private void avoidImmediateExecute(EventLoop instance, Runnable runnable, Operation<Void> original, Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean flush) {
        if (!flush && instance instanceof AbstractEventExecutor) {
            AbstractEventExecutor executor = (AbstractEventExecutor)instance;
            executor.lazyExecute(runnable);
        } else {
            original.call(new Object[]{instance, runnable});
        }
    }
}

