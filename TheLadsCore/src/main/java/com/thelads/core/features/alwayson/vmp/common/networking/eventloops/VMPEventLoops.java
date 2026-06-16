/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Suppliers
 *  com.google.common.util.concurrent.ThreadFactoryBuilder
 *  io.netty.channel.Channel
 *  io.netty.channel.EventLoopGroup
 *  io.netty.channel.epoll.EpollEventLoopGroup
 *  io.netty.channel.epoll.EpollSocketChannel
 *  io.netty.channel.nio.NioEventLoopGroup
 *  io.netty.channel.socket.nio.NioSocketChannel
 *  io.netty.util.concurrent.FastThreadLocalThread
 *  net.minecraft.network.ConnectionProtocol
 */
package com.thelads.core.features.alwayson.vmp.common.networking.eventloops;

import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.util.function.Supplier;
import net.minecraft.network.ConnectionProtocol;

public class VMPEventLoops {
    public static final Supplier<NioEventLoopGroup> NIO_LOGIN_EVENT_LOOP_GROUP = Suppliers.memoize(() -> new NioEventLoopGroup(2, new ThreadFactoryBuilder().setThreadFactory(FastThreadLocalThread::new).setNameFormat("Netty Server Login IO #%d").setDaemon(true).build()));
    public static final Supplier<NioEventLoopGroup> NIO_PLAY_EVENT_LOOP_GROUP = Suppliers.memoize(() -> new NioEventLoopGroup(0, new ThreadFactoryBuilder().setThreadFactory(FastThreadLocalThread::new).setNameFormat("Netty Server Play IO #%d").setDaemon(true).build()));
    public static final Supplier<EpollEventLoopGroup> EPOLL_LOGIN_EVENT_LOOP_GROUP = Suppliers.memoize(() -> new EpollEventLoopGroup(2, new ThreadFactoryBuilder().setThreadFactory(FastThreadLocalThread::new).setNameFormat("Netty Epoll Server Login IO #%d").setDaemon(true).build()));
    public static final Supplier<EpollEventLoopGroup> EPOLL_PLAY_EVENT_LOOP_GROUP = Suppliers.memoize(() -> new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setThreadFactory(FastThreadLocalThread::new).setNameFormat("Netty Epoll Server Play IO #%d").setDaemon(true).build()));

    public static EventLoopGroup getEventLoopGroup(Channel channel, ConnectionProtocol state) {
        if (channel instanceof NioSocketChannel) {
            if (state == ConnectionProtocol.LOGIN) {
                return (EventLoopGroup)NIO_LOGIN_EVENT_LOOP_GROUP.get();
            }
            if (state == ConnectionProtocol.PLAY) {
                return (EventLoopGroup)NIO_PLAY_EVENT_LOOP_GROUP.get();
            }
        } else if (channel instanceof EpollSocketChannel) {
            if (state == ConnectionProtocol.LOGIN) {
                return (EventLoopGroup)EPOLL_LOGIN_EVENT_LOOP_GROUP.get();
            }
            if (state == ConnectionProtocol.PLAY) {
                return (EventLoopGroup)EPOLL_PLAY_EVENT_LOOP_GROUP.get();
            }
        }
        return null;
    }
}

