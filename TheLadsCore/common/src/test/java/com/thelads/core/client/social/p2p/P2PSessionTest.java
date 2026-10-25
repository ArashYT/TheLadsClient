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

    /** F3 (PocControl): the control stream is read with a 64 KB line cap instead of an unbounded readLine. */
    @Test
    public void controlLineOverCapFails() throws Exception {
        java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
        assertEquals("{\"a\":1}", P2PSessionImpl.readControlLine(new java.io.ByteArrayInputStream("{\"a\":1}\n".getBytes()), line));
        java.io.InputStream endless = new java.io.InputStream() {
            @Override public int read() { return 'A'; }
        };
        assertThrows(java.io.IOException.class, () -> P2PSessionImpl.readControlLine(endless, line));
        assertTrue(line.size() <= P2PSessionImpl.MAX_CONTROL_LINE);
    }

    /** F2 (PocStall): with the peer gone silent and the send window full, close() returns at once and the stuck writer is freed. */
    @Test
    public void closeNeverBlocksOnAStuckWriter() throws Exception {
        String sid = "test-stuck-close";
        P2PSession.IceConfig cfg = new P2PSession.IceConfig(Collections.emptyList(), Collections.emptyList(), false);
        BlockingQueue<JsonObject> toGuest = new LinkedBlockingQueue<>(), toHost = new LinkedBlockingQueue<>();
        CompletableFuture<P2PSession> hostF = P2PSession.start(sid, true, cfg, toGuest::add);
        CompletableFuture<P2PSession> guestF = P2PSession.start(sid, false, cfg, toHost::add);
        Thread pump = new Thread(() -> {
            try {
                while (true) {
                    JsonObject a = toGuest.poll(5, TimeUnit.MILLISECONDS);
                    if (a != null) ((P2PSession) guestF).onSignal(a);
                    JsonObject b = toHost.poll(5, TimeUnit.MILLISECONDS);
                    if (b != null) ((P2PSession) hostF).onSignal(b);
                }
            } catch (InterruptedException e) {
                // done
            }
        });
        pump.setDaemon(true);
        pump.start();
        P2PSession host = hostF.get(10, TimeUnit.SECONDS), guest = guestF.get(10, TimeUnit.SECONDS);
        pump.interrupt();
        // The guest's game freezes: its socket dies without a word, so nothing it got is ever acknowledged
        java.lang.reflect.Field ice = P2PSessionImpl.class.getDeclaredField("iceAgent");
        ice.setAccessible(true);
        ((IceAgent) ice.get(guest)).close();

        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            try {
                host.out(P2PSession.MINECRAFT).write(new byte[4_000_000]);
            } catch (java.io.IOException expected) {
                // freed by the close
            }
        });
        Thread.sleep(500); // the 256-packet window is full: the writer holds the mux output
        assertFalse(writer.isDone(), "the writer must be stuck for this test to mean anything");
        long start = System.nanoTime();
        host.close("Kicked");
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(ms < 100, "close() took " + ms + " ms on the caller thread");
        writer.get(3, TimeUnit.SECONDS); // the worker's hard close frees it after at most ~1 s
        guest.close("test end");
    }
}
