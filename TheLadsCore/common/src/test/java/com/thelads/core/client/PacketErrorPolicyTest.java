package com.thelads.core.client;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacketErrorPolicyTest {
    /** Stands in for Netty's ReadTimeoutException, which common can't see. */
    static final class ReadTimeoutException extends RuntimeException { }

    @Test void unreadableOrUnhandledPacketsAreSkipped() {
        assertTrue(PacketErrorPolicy.skippable(new IOException("Bad packet id 255")));
        assertTrue(PacketErrorPolicy.skippable(new RuntimeException("Failed to decode packet", new IndexOutOfBoundsException())));
        assertTrue(PacketErrorPolicy.skippable(new IllegalStateException("handler failed", new NullPointerException())));
    }

    @Test void brokenConnectionsAndBrokenGamesStillDisconnect() {
        assertFalse(PacketErrorPolicy.skippable(new ReadTimeoutException()));
        assertFalse(PacketErrorPolicy.skippable(new RuntimeException(new TimeoutException())));
        assertFalse(PacketErrorPolicy.skippable(new RuntimeException(new SocketException("Connection reset"))));
        assertFalse(PacketErrorPolicy.skippable(new IOException(new ClosedChannelException())));
        assertFalse(PacketErrorPolicy.skippable(new RuntimeException(new OutOfMemoryError())));
        assertFalse(PacketErrorPolicy.skippable(null));
    }

    @Test void causeCyclesEnd() {
        var first = new RuntimeException("a"); var second = new RuntimeException("b", first); first.initCause(second);
        assertTrue(PacketErrorPolicy.skippable(first));
    }
}
