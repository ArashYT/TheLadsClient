package com.thelads.core.client;

import com.thelads.core.client.cosmetics.backend.CosmeticsBackend;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsPerfTest {

    @Test
    public void testFetchByUsernameBlocksThreads() throws Exception {
        int numTasks = 50;
        CountDownLatch latch = new CountDownLatch(numTasks);
        
        for (int i = 0; i < numTasks; i++) {
            CompletableFuture<net.minecraft.resources.Identifier> f = CosmeticsBackend.fetchByUsername("dummy_user_" + i, true);
            f.whenComplete((res, ex) -> {
                latch.countDown();
            });
        }
        
        Thread.sleep(200);
        
        int blockedThreads = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().contains("ForkJoinPool.commonPool-worker")) {
                boolean isBlocked = false;
                for (StackTraceElement ste : t.getStackTrace()) {
                    if (ste.getMethodName().equals("send") && ste.getClassName().contains("HttpClient")) {
                        isBlocked = true;
                    }
                }
                if (isBlocked) {
                    blockedThreads++;
                }
            }
        }
        
        System.out.println("Blocked ForkJoinPool threads on HttpClient.send: " + blockedThreads);
        assertEquals(0, blockedThreads, "Expected NO ForkJoinPool threads to be blocked on HttpClient.send because fetchByUsername should be asynchronous!");
        
        latch.await();
    }
}
