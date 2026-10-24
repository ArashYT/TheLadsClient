package com.thelads.core.client.social.p2p;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ReliableStreamTest {

    @Test
    public void survivesTenPercentLossInOrder() throws Exception {
        Random dropRand = new Random(42);
        AtomicReference<ReliableStream> streamARef = new AtomicReference<>();
        AtomicReference<ReliableStream> streamBRef = new AtomicReference<>();

        ArrayBlockingQueue<byte[]> queueAtoB = new ArrayBlockingQueue<>(1024);
        ArrayBlockingQueue<byte[]> queueBtoA = new ArrayBlockingQueue<>(1024);
        AtomicBoolean running = new AtomicBoolean(true);

        Thread threadAtoB = new Thread(() -> {
            while (running.get()) {
                try {
                    byte[] pkt = queueAtoB.poll(10, TimeUnit.MILLISECONDS);
                    if (pkt == null) continue;
                    if (dropRand.nextInt(100) >= 10) { // 10% packet drop
                        ReliableStream b = streamBRef.get();
                        if (b != null) b.onDatagramReceived(pkt, 0, pkt.length);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "SimNet-AtoB");
        threadAtoB.setDaemon(true);
        threadAtoB.start();

        Thread threadBtoA = new Thread(() -> {
            while (running.get()) {
                try {
                    byte[] pkt = queueBtoA.poll(10, TimeUnit.MILLISECONDS);
                    if (pkt == null) continue;
                    if (dropRand.nextInt(100) >= 10) { // 10% packet drop
                        ReliableStream a = streamARef.get();
                        if (a != null) a.onDatagramReceived(pkt, 0, pkt.length);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "SimNet-BtoA");
        threadBtoA.setDaemon(true);
        threadBtoA.start();

        // In-memory lossy link: 10% packet drop, non-blocking UDP queue
        ReliableStream streamA = new ReliableStream((data, off, len) -> {
            byte[] copy = new byte[len];
            System.arraycopy(data, off, copy, 0, len);
            queueAtoB.offer(copy);
        });
        streamARef.set(streamA);

        ReliableStream streamB = new ReliableStream((data, off, len) -> {
            byte[] copy = new byte[len];
            System.arraycopy(data, off, copy, 0, len);
            queueBtoA.offer(copy);
        });
        streamBRef.set(streamB);

        try {
            int payloadSize = 5 * 1024 * 1024; // 5 MB
            byte[] testData = new byte[payloadSize];
            Random dataRand = new Random(12345);
            dataRand.nextBytes(testData);

            MessageDigest mdSender = MessageDigest.getInstance("SHA-256");
            mdSender.update(testData);
            byte[] expectedHash = mdSender.digest();

            // Receiver thread
            CompletableFuture<byte[]> receiverFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream in = streamB.getInputStream();
                    byte[] received = new byte[payloadSize];
                    int totalRead = 0;
                    byte[] chunk = new byte[16384];
                    while (totalRead < payloadSize) {
                        int n = in.read(chunk, 0, Math.min(chunk.length, payloadSize - totalRead));
                        if (n == -1) break;
                        System.arraycopy(chunk, 0, received, totalRead, n);
                        totalRead += n;
                    }
                    MessageDigest mdReceiver = MessageDigest.getInstance("SHA-256");
                    mdReceiver.update(received, 0, totalRead);
                    return mdReceiver.digest();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            // Sender thread writes 5 MB in chunks
            OutputStream out = streamA.getOutputStream();
            int offset = 0;
            int chunkSize = 16384;
            while (offset < payloadSize) {
                int toWrite = Math.min(chunkSize, payloadSize - offset);
                out.write(testData, offset, toWrite);
                offset += toWrite;
            }
            out.flush();

            byte[] actualHash = receiverFuture.get(60, TimeUnit.SECONDS);
            assertArrayEquals(expectedHash, actualHash, "SHA-256 hash must match despite 10% packet drop");
        } finally {
            streamA.close();
            streamB.close();
            running.set(false);
            threadAtoB.interrupt();
            threadBtoA.interrupt();
        }
    }

    @Test
    public void throughputAtLeast4MBpsOnLoopback() throws Exception {
        DatagramSocket sockA = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        DatagramSocket sockB = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));

        int portA = sockA.getLocalPort();
        int portB = sockB.getLocalPort();

        ReliableStream streamA = new ReliableStream((data, off, len) -> {
            DatagramPacket p = new DatagramPacket(data, off, len, InetAddress.getByName("127.0.0.1"), portB);
            sockA.send(p);
        });

        ReliableStream streamB = new ReliableStream((data, off, len) -> {
            DatagramPacket p = new DatagramPacket(data, off, len, InetAddress.getByName("127.0.0.1"), portA);
            sockB.send(p);
        });

        Thread readerA = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (!sockA.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    sockA.receive(p);
                    streamA.onDatagramReceived(buf, 0, p.getLength());
                } catch (Exception ignored) {
                }
            }
        });
        readerA.setDaemon(true);
        readerA.start();

        Thread readerB = new Thread(() -> {
            byte[] buf = new byte[2048];
            while (!sockB.isClosed()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    sockB.receive(p);
                    streamB.onDatagramReceived(buf, 0, p.getLength());
                } catch (Exception ignored) {
                }
            }
        });
        readerB.setDaemon(true);
        readerB.start();

        try {
            int payloadSize = 8 * 1024 * 1024; // 8 MB
            byte[] testData = new byte[payloadSize];
            new Random(999).nextBytes(testData);

            long startTime = System.currentTimeMillis();

            CompletableFuture<Integer> receiverFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream in = streamB.getInputStream();
                    byte[] chunk = new byte[16384];
                    int total = 0;
                    while (total < payloadSize) {
                        int n = in.read(chunk, 0, Math.min(chunk.length, payloadSize - total));
                        if (n == -1) break;
                        total += n;
                    }
                    return total;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            OutputStream out = streamA.getOutputStream();
            int off = 0;
            int chunk = 16384;
            while (off < payloadSize) {
                int toWrite = Math.min(chunk, payloadSize - off);
                out.write(testData, off, toWrite);
                off += toWrite;
            }
            out.flush();

            int totalReceived = receiverFuture.get(30, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - startTime;
            assertEquals(payloadSize, totalReceived);

            double seconds = Math.max(0.001, elapsedMs / 1000.0);
            double mbps = (payloadSize / (1024.0 * 1024.0)) / seconds;

            assertTrue(mbps >= 4.0, "Throughput must be at least 4.0 MB/s, got: " + String.format("%.2f", mbps) + " MB/s");
        } finally {
            streamA.close();
            streamB.close();
            sockA.close();
            sockB.close();
        }
    }
}
