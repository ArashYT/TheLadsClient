package com.thelads.core.client.cosmetics.backend;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsHeadlessStressTest {

    @BeforeEach
    public void setup() throws Exception {
        // Set dummy registrar
        CosmeticsBackend.setRegistrar(data -> {
            return CompletableFuture.completedFuture(Identifier.fromNamespaceAndPath("dummy", "dummy_" + UUID.randomUUID().toString()));
        });

        // Clear maps via reflection
        Field skins = CosmeticsBackend.class.getDeclaredField("activeSkins");
        skins.setAccessible(true);
        ((java.util.Map) skins.get(null)).clear();

        Field capes = CosmeticsBackend.class.getDeclaredField("activeCapes");
        capes.setAccessible(true);
        ((java.util.Map) capes.get(null)).clear();

        Field refCounts = CosmeticsBackend.class.getDeclaredField("refCounts");
        refCounts.setAccessible(true);
        ((java.util.Map) refCounts.get(null)).clear();

        Field unassigned = CosmeticsBackend.class.getDeclaredField("unassigned");
        unassigned.setAccessible(true);
        ((java.util.Map) unassigned.get(null)).clear();
    }

    @Test
    public void testHeadlessReleaseNPE() {
        UUID u1 = UUID.randomUUID();
        Identifier id1 = Identifier.fromNamespaceAndPath("test", "test_id");
        
        // This puts it in refCounts
        CosmeticsBackend.setActiveSkin(u1, id1);

        // This removes it, making refCount <= 0 and triggering Minecraft.getInstance().execute(...)
        // In headless, Minecraft.getInstance() might be null or throw.
        try {
            CosmeticsBackend.setActiveSkin(u1, null);
        } catch (Exception e) {
            fail("Exception thrown during clear: " + e);
        }
    }
}
