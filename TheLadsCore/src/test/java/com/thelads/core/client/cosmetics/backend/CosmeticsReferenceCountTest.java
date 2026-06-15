package com.thelads.core.client.cosmetics.backend;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CosmeticsReferenceCountTest {

    @Test
    public void testConcurrentSetActiveSkinLeak() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Identifier skinId = Identifier.fromNamespaceAndPath("thelads", "test_skin");
        
        // Use reflection to access refCounts
        Field refCountsField = CosmeticsBackend.class.getDeclaredField("refCounts");
        refCountsField.setAccessible(true);
        Map<Identifier, Integer> refCounts = (Map<Identifier, Integer>) refCountsField.get(null);
        
        int numThreads = 100;
        CountDownLatch latch = new CountDownLatch(1);
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) { }
                CosmeticsBackend.setActiveSkin(playerUuid, skinId);
            });
            threads[i].start();
        }
        
        latch.countDown();
        for (Thread t : threads) {
            t.join();
        }
        
        // Because of concurrent setActiveSkin to the SAME uuid and SAME skin,
        // refCounts will be incremented 100 times, but it only "replaces" itself,
        // which skips decrementing due to `!oldIdentifier.equals(skin)`.
        // So refCount is 100, but there's only 1 active reference!
        // When we clear the active skin, it decrements by 1.
        CosmeticsBackend.clearActiveSkin(playerUuid);
        
        // The remaining count should be 0, but it will be 99!
        int remaining = refCounts.getOrDefault(skinId, 0);
        assertEquals(0, remaining, "Memory leak: refCount is " + remaining + " but should be 0");
    }
}
