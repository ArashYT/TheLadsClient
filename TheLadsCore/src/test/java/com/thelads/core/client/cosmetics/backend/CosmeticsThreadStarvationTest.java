package com.thelads.core.client.cosmetics.backend;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CosmeticsThreadStarvationTest {

    @Test
    public void testNioThreadStarvationOnFetchByFile() throws Exception {
        ConcurrentHashMap<String, Boolean> threadNames = new ConcurrentHashMap<>();
        CosmeticsBackend.setRegistrar(data -> {
            threadNames.put(Thread.currentThread().getName(), true);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
            return CompletableFuture.completedFuture(net.minecraft.resources.Identifier.fromNamespaceAndPath("test", "test"));
        });

        File tempFile = Files.createTempFile("test_skin", ".png").toFile();
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[1024]);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(CosmeticsBackend.fetchByFile(tempFile));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        try (FileOutputStream fos = new FileOutputStream("THREADS_USED.txt")) {
            fos.write(threadNames.keySet().toString().getBytes());
        }
    }
}
