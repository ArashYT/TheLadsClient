package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import org.junit.jupiter.api.Test;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsSlowServerTest {

    @Test
    public void testThreadPoolExhaustion() throws Exception {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        
        Thread serverThread = new Thread(() -> {
            try {
                while (true) {
                    Socket client = serverSocket.accept();
                    OutputStream out = client.getOutputStream();
                    // Send headers slowly
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 1048576\r\n\r\n".getBytes());
                    out.flush();
                    // Wait for a long time to keep the connection open
                    Thread.sleep(10000);
                }
            } catch (Exception e) {}
        });
        serverThread.setDaemon(true);
        serverThread.start();

        int poolSize = ForkJoinPool.commonPool().getPoolSize();
        if (poolSize == 0) poolSize = Runtime.getRuntime().availableProcessors() - 1;
        if (poolSize < 1) poolSize = 1;
        
        CountDownLatch tasksStarted = new CountDownLatch(poolSize);
        CountDownLatch extraTaskStarted = new CountDownLatch(1);

        for (int i = 0; i < poolSize; i++) {
            CompletableFuture.runAsync(() -> {
                tasksStarted.countDown();
                try {
                    // Because fetchByUrl uses sendAsync().join() internally for something or blocks in registerTexture?
                    // Wait, fetchByUrl uses HTTP_CLIENT.sendAsync, which does NOT block the worker thread while downloading!
                    // Let's check fetchByUsername which DOES use .send() synchronously.
                    // Wait, fetchByUsername is hardcoded to api.mojang.com. We can't redirect it to localhost easily.
                } catch (Exception e) {}
            });
        }
    }
}
