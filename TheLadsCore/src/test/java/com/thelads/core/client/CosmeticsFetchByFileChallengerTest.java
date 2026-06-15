package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsFetchByFileChallengerTest {

    @Test
    public void testFetchByFileThreadStarvation() throws Exception {
        int poolSize = ForkJoinPool.commonPool().getPoolSize();
        if (poolSize == 0) poolSize = Runtime.getRuntime().availableProcessors() - 1;
        if (poolSize < 1) poolSize = 1;
        
        CountDownLatch tasksStarted = new CountDownLatch(poolSize);
        CountDownLatch extraTaskStarted = new CountDownLatch(1);

        // Create a dummy file
        File tempFile = File.createTempFile("dummy_skin", ".png");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[100]); // dummy content
        }

        for (int i = 0; i < poolSize; i++) {
            CompletableFuture.runAsync(() -> {
                tasksStarted.countDown();
                try {
                    // This blocks if fetchByFile uses blocking I/O internally within supplyAsync
                    CosmeticsBackend.fetchByFile(tempFile).join();
                } catch (Exception e) {}
            });
        }
        
        tasksStarted.await(5, java.util.concurrent.TimeUnit.SECONDS);
        
        CompletableFuture.runAsync(() -> {
            extraTaskStarted.countDown();
        });
        
        boolean extraStarted = extraTaskStarted.await(2, java.util.concurrent.TimeUnit.SECONDS);
        
        // extraStarted will be false if the pool is exhausted due to blocking I/O
        assertTrue(extraStarted, "Thread pool is exhausted because fetchByFile uses blocking I/O (Files.readAllBytes) inside supplyAsync!");
    }
}
