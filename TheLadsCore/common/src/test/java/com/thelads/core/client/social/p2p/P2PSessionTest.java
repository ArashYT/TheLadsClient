package com.thelads.core.client.social.p2p;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class P2PSessionTest {

    @Test
    public void hostCloseEndsGuestStreamWithinOneSecond() throws Exception {
        String sid = "test-host-close";
        P2PSession.IceConfig cfg = new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false);

        BlockingQueue<JsonObject> hostToGuest = new LinkedBlockingQueue<>();
        BlockingQueue<JsonObject> guestToHost = new LinkedBlockingQueue<>();

        CompletableFuture<P2PSession> hostFuture = P2PSession.start(sid, true, cfg, hostToGuest::add);
        CompletableFuture<P2PSession> guestFuture = P2PSession.start(sid, false, cfg, guestToHost::add);

        // Signal relay pump simulating WebSocket signaling
        Thread signalPump = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    JsonObject s1 = hostToGuest.poll(5, TimeUnit.MILLISECONDS);
                    if (s1 != null) {
                        guestFuture.thenAccept(g -> g.onSignal(s1));
                        // Also deliver immediately if guestFuture has starting instance
                        if (guestFuture instanceof P2PSession) {
                            ((P2PSession) guestFuture).onSignal(s1);
                        }
                    }
                    JsonObject s2 = guestToHost.poll(5, TimeUnit.MILLISECONDS);
                    if (s2 != null) {
                        hostFuture.thenAccept(h -> h.onSignal(s2));
                        if (hostFuture instanceof P2PSession) {
                            ((P2PSession) hostFuture).onSignal(s2);
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "SignalPump");
        signalPump.setDaemon(true);
        signalPump.start();

        try {
            P2PSession hostSession = hostFuture.get(10, TimeUnit.SECONDS);
            P2PSession guestSession = guestFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(hostSession);
            assertNotNull(guestSession);

            // Register guest onClose callback
            AtomicReference<String> guestCloseReason = new AtomicReference<>();
            CountDownLatch closeLatch = new CountDownLatch(1);
            guestSession.onClose(reason -> {
                guestCloseReason.set(reason);
                closeLatch.countDown();
            });

            // Guest begins blocking read on stream 0 (Minecraft)
            InputStream guestIn = guestSession.in(P2PSession.MINECRAFT);
            AtomicBoolean readFinished = new AtomicBoolean(false);
            AtomicInteger readResult = new AtomicInteger(0);

            Thread readerThread = new Thread(() -> {
                try {
                    int b = guestIn.read();
                    readResult.set(b);
                    readFinished.set(true);
                } catch (Exception e) {
                    readResult.set(-1);
                    readFinished.set(true);
                }
            }, "GuestReader");
            readerThread.start();

            // Give reader thread a moment to enter in.read()
            Thread.sleep(50);

            // Host closes the world
            long closeStartTime = System.currentTimeMillis();
            hostSession.close("Host closed the world");

            // Guest stream 0 must unblock and return -1 within 1 second
            readerThread.join(1000);
            long elapsedMs = System.currentTimeMillis() - closeStartTime;

            assertTrue(readFinished.get(), "Guest stream 0 read() must unblock within 1 second of host close (took " + elapsedMs + " ms)");
            assertEquals(-1, readResult.get(), "Guest stream 0 read() must return -1 (EOF)");

            // Guest onClose callback must be called with "Host closed the world"
            boolean callbackInvoked = closeLatch.await(1000, TimeUnit.MILLISECONDS);
            assertTrue(callbackInvoked, "Guest onClose callback must be invoked within 1 second");
            assertEquals("Host closed the world", guestCloseReason.get(), "Actual reason received was: " + guestCloseReason.get());

        } finally {
            signalPump.interrupt();
        }
    }

    @Test
    public void bidirectionalStreamTransfer() throws Exception {
        String sid = "test-bidirectional";
        P2PSession.IceConfig cfg = new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false);

        BlockingQueue<JsonObject> hostToGuest = new LinkedBlockingQueue<>();
        BlockingQueue<JsonObject> guestToHost = new LinkedBlockingQueue<>();

        CompletableFuture<P2PSession> hostFuture = P2PSession.start(sid, true, cfg, hostToGuest::add);
        CompletableFuture<P2PSession> guestFuture = P2PSession.start(sid, false, cfg, guestToHost::add);

        Thread signalPump = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    JsonObject s1 = hostToGuest.poll(5, TimeUnit.MILLISECONDS);
                    if (s1 != null && guestFuture instanceof P2PSession) {
                        ((P2PSession) guestFuture).onSignal(s1);
                    }
                    JsonObject s2 = guestToHost.poll(5, TimeUnit.MILLISECONDS);
                    if (s2 != null && hostFuture instanceof P2PSession) {
                        ((P2PSession) hostFuture).onSignal(s2);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "SignalPump");
        signalPump.setDaemon(true);
        signalPump.start();

        try {
            P2PSession hostSession = hostFuture.get(10, TimeUnit.SECONDS);
            P2PSession guestSession = guestFuture.get(10, TimeUnit.SECONDS);

            byte[] hostMsg = "Hello from Host via Minecraft stream!".getBytes(StandardCharsets.UTF_8);
            hostSession.out(P2PSession.MINECRAFT).write(hostMsg);
            hostSession.out(P2PSession.MINECRAFT).flush();

            byte[] guestBuf = new byte[hostMsg.length];
            int readTotal = 0;
            while (readTotal < hostMsg.length) {
                int n = guestSession.in(P2PSession.MINECRAFT).read(guestBuf, readTotal, hostMsg.length - readTotal);
                if (n == -1) break;
                readTotal += n;
            }
            assertEquals(hostMsg.length, readTotal);
            assertArrayEquals(hostMsg, guestBuf);

            byte[] guestMsg = "Hello from Guest via Minecraft stream!".getBytes(StandardCharsets.UTF_8);
            guestSession.out(P2PSession.MINECRAFT).write(guestMsg);
            guestSession.out(P2PSession.MINECRAFT).flush();

            byte[] hostBuf = new byte[guestMsg.length];
            readTotal = 0;
            while (readTotal < guestMsg.length) {
                int n = hostSession.in(P2PSession.MINECRAFT).read(hostBuf, readTotal, guestMsg.length - readTotal);
                if (n == -1) break;
                readTotal += n;
            }
            assertEquals(guestMsg.length, readTotal);
            assertArrayEquals(guestMsg, hostBuf);

            assertTrue(hostSession.bytes() > 0, "Bytes counted must be > 0");
            assertTrue(guestSession.bytes() > 0, "Bytes counted must be > 0");

            hostSession.close("Session finished");
            guestSession.close("Session finished");
        } finally {
            signalPump.interrupt();
        }
    }
}
