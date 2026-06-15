package com.thelads.core.client.cosmetics.backend;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class CosmeticsHeadlessTest {

    @Test
    public void testHeadlessCleanup() throws Exception {
        CosmeticsBackend.setRegistrar(data -> CompletableFuture.completedFuture(Identifier.fromNamespaceAndPath("thelads", "dummy_" + UUID.randomUUID().toString())));
        
        UUID uuid = UUID.randomUUID();
        Identifier id = Identifier.fromNamespaceAndPath("thelads", "dummy_" + UUID.randomUUID().toString());
        
        CosmeticsBackend.setActiveSkin(uuid, id);
        
        // Clearing it drops ref count to 0, which triggers:
        // Minecraft.getInstance().execute(...)
        // In a headless unit test, Minecraft.getInstance() is null.
        // This will throw a NullPointerException inside clearActiveSkin.
        CosmeticsBackend.clearActiveSkin(uuid);
    }
}
