package com.thelads.core.client;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IgnorePacketErrors: which client-side packet failures may be skipped instead of disconnecting. A packet that can't be read
 * or handled is skipped; a broken transport (timeout, closed or reset socket) or a broken game (any Error, e.g. out of memory)
 * still disconnects as vanilla does.
 */
public final class PacketErrorPolicy {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final AtomicInteger SKIPPED = new AtomicInteger();
    private PacketErrorPolicy() {}

    public static boolean skippable(Throwable failure) {
        int depth = 0;
        for (Throwable cause = failure; cause != null && depth++ < 32; cause = cause.getCause()) {
            if (cause instanceof Error || cause instanceof java.net.SocketException || cause instanceof java.nio.channels.ClosedChannelException
                || cause instanceof java.util.concurrent.TimeoutException || cause.getClass().getSimpleName().endsWith("TimeoutException")) return false;
        }
        return failure != null;
    }

    /** Packets skipped since the game started (QA reads it). */
    public static int skippedCount() { return SKIPPED.get(); }

    /** Logs a skipped packet: the stack trace for the first few, one line after that so a broken server can't flood the log. */
    public static void skipped(String what, Throwable failure) {
        int count = SKIPPED.incrementAndGet();
        if (count <= 3) LOGGER.warn("Lads IgnorePacketErrors: skipped {} instead of disconnecting (#{})", what, count, failure);
        else LOGGER.warn("Lads IgnorePacketErrors: skipped {} instead of disconnecting (#{}): {}", what, count, failure.toString());
    }
}
