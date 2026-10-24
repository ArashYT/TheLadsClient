package com.thelads.core.client.social.p2p;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class MuxTest {

    @Test
    public void streamsIndependentAndCloseIsolated() throws Exception {
        PipedOutputStream rawOutA = new PipedOutputStream();
        PipedInputStream rawInB = new PipedInputStream(rawOutA, 64 * 1024);

        PipedOutputStream rawOutB = new PipedOutputStream();
        PipedInputStream rawInA = new PipedInputStream(rawOutB, 64 * 1024);

        Mux muxA = new Mux(rawInA, rawOutA);
        Mux muxB = new Mux(rawInB, rawOutB);

        try {
            // Test concurrent writes on stream 0 and stream 1
            byte[] msg0 = "Minecraft traffic on stream 0".getBytes(StandardCharsets.UTF_8);
            byte[] msg1 = "Control traffic on stream 1".getBytes(StandardCharsets.UTF_8);

            muxA.out(0).write(msg0);
            muxA.out(0).flush();

            muxA.out(1).write(msg1);
            muxA.out(1).flush();

            // Read on muxB stream 0
            byte[] read0 = new byte[msg0.length];
            int n0 = muxB.in(0).read(read0);
            assertEquals(msg0.length, n0);
            assertArrayEquals(msg0, read0);

            // Read on muxB stream 1
            byte[] read1 = new byte[msg1.length];
            int n1 = muxB.in(1).read(read1);
            assertEquals(msg1.length, n1);
            assertArrayEquals(msg1, read1);

            // Close stream 1 on A
            muxA.out(1).close();

            // B should see EOF (-1) on stream 1
            int eof1 = muxB.in(1).read();
            assertEquals(-1, eof1, "Stream 1 must reach EOF when closed");

            // Stream 0 MUST continue to work independently in both directions!
            byte[] msg0Second = "More Minecraft traffic after stream 1 closed".getBytes(StandardCharsets.UTF_8);
            muxA.out(0).write(msg0Second);
            muxA.out(0).flush();

            byte[] read0Second = new byte[msg0Second.length];
            int n0Second = muxB.in(0).read(read0Second);
            assertEquals(msg0Second.length, n0Second);
            assertArrayEquals(msg0Second, read0Second);

            // B writes to A on stream 0
            byte[] msg0BtoA = "Hello from B on stream 0".getBytes(StandardCharsets.UTF_8);
            muxB.out(0).write(msg0BtoA);
            muxB.out(0).flush();

            byte[] read0BtoA = new byte[msg0BtoA.length];
            int n0BtoA = muxA.in(0).read(read0BtoA);
            assertEquals(msg0BtoA.length, n0BtoA);
            assertArrayEquals(msg0BtoA, read0BtoA);

            // Close stream 0 on A
            muxA.out(0).close();
            int eof0 = muxB.in(0).read();
            assertEquals(-1, eof0, "Stream 0 must reach EOF when closed");

        } finally {
            muxA.close();
            muxB.close();
        }
    }

    @Test
    public void stalledStream0ReaderDoesNotBlockControlStream() throws Exception {
        PipedOutputStream rawOutA = new PipedOutputStream();
        PipedInputStream rawInB = new PipedInputStream(rawOutA, 128 * 1024);
        PipedOutputStream rawOutB = new PipedOutputStream();
        PipedInputStream rawInA = new PipedInputStream(rawOutB, 128 * 1024);
        Mux muxA = new Mux(rawInA, rawOutA);
        Mux muxB = new Mux(rawInB, rawOutB);
        try {
            // Stream 0 sends 600 KB (> the 512 KB credit) while its reader on B stalls
            byte[] chunk = new byte[8192];
            for (int i = 0; i < chunk.length; i++) chunk[i] = (byte) (i & 0xFF);
            CompletableFuture<Void> writer0 = CompletableFuture.runAsync(() -> {
                try {
                    OutputStream out0 = muxA.out(0);
                    for (int sent = 0; sent < 600 * 1024; sent += chunk.length) out0.write(chunk);
                    out0.flush();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(200);

            byte[] msg1 = "Control line past a stalled stream 0".getBytes(StandardCharsets.UTF_8);
            muxA.out(1).write(msg1);
            muxA.out(1).flush();
            byte[] read1 = new byte[msg1.length];
            int total1 = 0;
            long deadline = System.currentTimeMillis() + 3000;
            while (total1 < msg1.length && System.currentTimeMillis() < deadline) {
                int n = muxB.in(1).read(read1, total1, msg1.length - total1);
                if (n > 0) total1 += n;
            }
            assertArrayEquals(msg1, read1, "the control stream keeps flowing while stream 0 waits for credit");

            byte[] drain = new byte[8192];
            int total0 = 0;
            deadline = System.currentTimeMillis() + 5000;
            while (total0 < 600 * 1024 && System.currentTimeMillis() < deadline) {
                int n = muxB.in(0).read(drain);
                if (n > 0) total0 += n;
            }
            assertEquals(600 * 1024, total0, "stream 0 resumes once drained");
            assertDoesNotThrow(() -> writer0.get(3, TimeUnit.SECONDS));
        } finally {
            muxA.close();
            muxB.close();
        }
    }

    /** F1 (PocMux): one 5-byte frame per stream id used to allocate a 520 KB ring each; now the first unknown id ends the mux. */
    @Test
    public void unknownStreamIdEndsTheMuxWithoutAllocating() throws Exception {
        PipedOutputStream feed = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream(feed, 1 << 16);
        Mux mux = new Mux(in, OutputStream.nullOutputStream());
        Runtime rt = Runtime.getRuntime();
        System.gc();
        long before = rt.totalMemory() - rt.freeMemory();
        int sent = 0;
        try {
            for (int i = 2; i < 65_536; i++) {
                feed.write(new byte[] {(byte) (i >> 8), (byte) i, (byte) Mux.FRAME_WINDOW_UPDATE, 0, 0});
                sent++;
            }
        } catch (IOException pipeClosed) {
            // the mux closed its input after the first unknown id
        }
        assertEquals(-1, mux.in(0).read(), "the Minecraft stream ends");
        assertEquals(-1, mux.in(1).read(), "the control stream ends");
        System.gc();
        long grewMb = ((rt.totalMemory() - rt.freeMemory()) - before) >> 20;
        assertTrue(grewMb < 16, "heap grew " + grewMb + " MB after " + sent + " frames");
        assertThrows(IllegalArgumentException.class, () -> mux.out(2), "no stream 2 until Phase 3 opens it");
    }

    /** F1: data beyond the control stream's buffer (it has no credit flow) ends the mux instead of growing or silently dropping. */
    @Test
    public void controlFloodEndsTheMux() throws Exception {
        PipedOutputStream feed = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream(feed, 1 << 16);
        Mux mux = new Mux(in, OutputStream.nullOutputStream());
        byte[] frame = new byte[5 + Mux.MAX_CHUNK_SIZE];
        frame[1] = 1;
        frame[2] = (byte) Mux.FRAME_DATA;
        StunMessage.putShortBE(frame, 3, (short) Mux.MAX_CHUNK_SIZE);
        boolean ended = false;
        try {
            for (int i = 0; i < 200; i++) feed.write(frame); // nobody reads the control stream meanwhile
        } catch (IOException pipeClosed) {
            ended = true; // the mux closed its input once the buffer overflowed
        }
        assertTrue(ended, "64 KB of unread control data must end the mux, not be dropped or grow");
        assertEquals(-1, mux.in(0).read());
    }
}
