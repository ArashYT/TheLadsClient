package com.thelads.core.client.social.p2p;

import org.junit.jupiter.api.Test;

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
    public void stream2ReaderStallsStream0KeepsFlowing() throws Exception {
        PipedOutputStream rawOutA = new PipedOutputStream();
        PipedInputStream rawInB = new PipedInputStream(rawOutA, 128 * 1024);

        PipedOutputStream rawOutB = new PipedOutputStream();
        PipedInputStream rawInA = new PipedInputStream(rawOutB, 128 * 1024);

        Mux muxA = new Mux(rawInA, rawOutA);
        Mux muxB = new Mux(rawInB, rawOutB);

        try {
            // Stream 2 writer sends 600 KB (> 512 KB receive window)
            // Stream 2 reader on B STALLS (does not read anything yet)
            byte[] stream2Chunk = new byte[8192];
            for (int i = 0; i < stream2Chunk.length; i++) stream2Chunk[i] = (byte) (i & 0xFF);

            CompletableFuture<Void> stream2Writer = CompletableFuture.runAsync(() -> {
                try {
                    OutputStream out2 = muxA.out(2);
                    for (int sent = 0; sent < 600 * 1024; sent += stream2Chunk.length) {
                        out2.write(stream2Chunk);
                    }
                    out2.flush();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Wait a moment for stream 2 to consume its 512 KB credit and stall
            Thread.sleep(200);

            // While stream 2 is stalled and its reader on B hasn't touched the stream,
            // stream 0 (Minecraft) MUST continue flowing freely!
            byte[] msg0 = "Minecraft world data flowing past stalled stream 2".getBytes(StandardCharsets.UTF_8);
            muxA.out(0).write(msg0);
            muxA.out(0).flush();

            byte[] read0 = new byte[msg0.length];
            int totalRead0 = 0;
            long deadline = System.currentTimeMillis() + 3000;
            while (totalRead0 < msg0.length && System.currentTimeMillis() < deadline) {
                int n = muxB.in(0).read(read0, totalRead0, msg0.length - totalRead0);
                if (n > 0) totalRead0 += n;
            }
            assertEquals(msg0.length, totalRead0, "Stream 0 must keep flowing even when stream 2 reader stalls");
            assertArrayEquals(msg0, read0);

            // Also test control stream 1 (high priority)
            byte[] msg1 = "Control ping flowing past stalled stream 2".getBytes(StandardCharsets.UTF_8);
            muxA.out(1).write(msg1);
            muxA.out(1).flush();

            byte[] read1 = new byte[msg1.length];
            int totalRead1 = 0;
            deadline = System.currentTimeMillis() + 3000;
            while (totalRead1 < msg1.length && System.currentTimeMillis() < deadline) {
                int n = muxB.in(1).read(read1, totalRead1, msg1.length - totalRead1);
                if (n > 0) totalRead1 += n;
            }
            assertEquals(msg1.length, totalRead1, "Stream 1 must keep flowing even when stream 2 reader stalls");
            assertArrayEquals(msg1, read1);

            // Now consume stream 2 on B so stream 2 writer can complete
            InputStream in2 = muxB.in(2);
            byte[] drainBuf = new byte[8192];
            int totalRead2 = 0;
            deadline = System.currentTimeMillis() + 5000;
            while (totalRead2 < 600 * 1024 && System.currentTimeMillis() < deadline) {
                int n = in2.read(drainBuf);
                if (n > 0) totalRead2 += n;
            }
            assertEquals(600 * 1024, totalRead2, "Stream 2 must resume flowing once drained");
            assertDoesNotThrow(() -> stream2Writer.get(3, TimeUnit.SECONDS));
        } finally {
            muxA.close();
            muxB.close();
        }
    }
}
