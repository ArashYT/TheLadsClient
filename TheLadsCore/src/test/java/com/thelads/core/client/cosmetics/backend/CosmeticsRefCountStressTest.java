package com.thelads.core.client.cosmetics.backend;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsRefCountStressTest {

    @BeforeEach
    public void setup() throws Exception {
        // Set dummy registrar to avoid NPE during cleanup if it happens
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
    public void testRefCountLeakOnDuplicateSet() throws Exception {
        UUID u1 = UUID.randomUUID();
        Identifier id1 = Identifier.fromNamespaceAndPath("test", "test_id");

        // 1st assignment
        CosmeticsBackend.setActiveSkin(u1, id1);
        
        Field refCountsField = CosmeticsBackend.class.getDeclaredField("refCounts");
        refCountsField.setAccessible(true);
        java.util.Map<Identifier, Integer> refCounts = (java.util.Map<Identifier, Integer>) refCountsField.get(null);
        
        assertEquals(1, refCounts.get(id1), "RefCount should be 1 after first set");

        // 2nd assignment of the same skin to the same UUID
        CosmeticsBackend.setActiveSkin(u1, id1);
        
        // RefCount will be 2 because the code does not decrement if oldIdentifier.equals(skin)
        // But the UUID only "holds" 1 reference logically
        assertEquals(1, refCounts.get(id1), "RefCount should STILL be 1 after duplicate set");
    }

}
