package com.thelads.core.v1_8_9.perf;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty 4.0 has no FlushConsolidationHandler: this is the same idea. A flush request is answered once per event-loop pass
 * (executed after the tasks already queued), so writes that land in the same pass share one socket flush. Close, disconnect,
 * errors and writability changes flush at once. No timer; nothing is reordered or delayed past the next loop pass.
 */
public final class FlushBatchHandler189 extends ChannelDuplexHandler {
    private final AtomicBoolean scheduled = new AtomicBoolean();

    @Override
    public void flush(final ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive() || !scheduled.compareAndSet(false, true)) {
            if (!ctx.channel().isActive()) ctx.flush();
            return;
        }
        try {
            ctx.channel().eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    scheduled.set(false);
                    ctx.flush();
                }
            });
        } catch (RejectedExecutionException closing) {
            scheduled.set(false);
            ctx.flush();
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.flush();
        ctx.close(promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.flush();
        ctx.disconnect(promise);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.channel().isWritable()) ctx.flush();
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.flush();
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
        ctx.fireChannelInactive();
    }
}
