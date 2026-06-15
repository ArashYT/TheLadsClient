package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsUsernameChallengerTest {

    @Test
    public void testThreadPoolExhaustionBySyncSend() throws Exception {
        int poolSize = ForkJoinPool.commonPool().getPoolSize();
        if (poolSize == 0) poolSize = Runtime.getRuntime().availableProcessors() - 1;
        if (poolSize < 1) poolSize = 1;
        
        CountDownLatch tasksStarted = new CountDownLatch(poolSize);
        CountDownLatch extraTaskStarted = new CountDownLatch(1);

        for (int i = 0; i < poolSize; i++) {
            CompletableFuture.runAsync(() -> {
                tasksStarted.countDown();
                try {
                    // fetchByUsername uses synchronous HTTP_CLIENT.send() which blocks!
                    // This will exhaust the pool.
                    CosmeticsBackend.fetchByUsername("someuser" + System.nanoTime(), true).join();
                } catch (Exception e) {}
            });
        }
        
        tasksStarted.await(5, java.util.concurrent.TimeUnit.SECONDS);
        
        CompletableFuture.runAsync(() -> {
            extraTaskStarted.countDown();
        });
        
        boolean extraStarted = extraTaskStarted.await(2, java.util.concurrent.TimeUnit.SECONDS);
        
        // Wait, if it didn't start, it means the threads are blocked in .send()
        assertTrue(extraStarted, "Thread pool is exhausted because fetchByUsername uses synchronous .send() inside supplyAsync!");
    }
}
