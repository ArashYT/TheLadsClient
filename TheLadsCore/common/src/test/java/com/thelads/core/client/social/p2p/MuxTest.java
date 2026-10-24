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
}
