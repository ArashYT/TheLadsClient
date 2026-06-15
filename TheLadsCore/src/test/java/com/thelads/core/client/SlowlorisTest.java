package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class SlowlorisTest {

    @Test
    public void testThreadStarvationOnSlowNetwork() throws Exception {
        // Start a slow server that sends headers quickly but body infinitely slowly
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        
        Thread serverThread = new Thread(() -> {
            try {
                Socket client = serverSocket.accept();
                OutputStream out = client.getOutputStream();
                out.write("HTTP/1.1 200 OK\r\nContent-Length: 1048576\r\n\r\n".getBytes());
                out.flush();
                
                // keep trickling data slowly to keep the stream open
                for (int i = 0; i < 100; i++) {
                    Thread.sleep(1000);
                    out.write(new byte[]{0x00});
                    out.flush();
                }
                client.close();
            } catch (Exception e) {
                // ignore
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        
        long beforeThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().contains("ForkJoinPool.commonPool-worker")).count();

        CompletableFuture<?> future = CosmeticsBackend.fetchByUrl("http://localhost:" + port);
        
        // Wait for the CompletableFuture to timeout
        try {
            future.join();
            fail("Expected a timeout exception");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof java.util.concurrent.TimeoutException || e instanceof java.util.concurrent.CompletionException);
        }
        
        // Wait a bit to see if the thread is freed
        Thread.sleep(1000);
        
        boolean isLeaked = false;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("ForkJoinPool.commonPool-worker")) {
                for (StackTraceElement ste : t.getStackTrace()) {
                    if (ste.getMethodName().equals("read") && ste.getClassName().contains("InputStream")) {
                        isLeaked = true;
                        break;
                    }
                }
                if (isLeaked) {
                    System.out.println("LEAKED THREAD: " + t.getName());
                    for (StackTraceElement ste : t.getStackTrace()) {
                        System.out.println("\t" + ste);
                    }
                    break;
                }
            }
        }
        assertFalse(isLeaked, "A thread in the common pool should NOT be leaked or blocked in some InputStream.read");
        serverSocket.close();
    }
}
