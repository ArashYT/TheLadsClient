package com.thelads.core.client.cosmetics.backend;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class CosmeticsBackendTest {

    @Test
    public void testNullUuidHandling() {
        // ConcurrentHashMap throws NullPointerException if key is null
        // We fixed it to return null or exit early.
        assertDoesNotThrow(() -> {
            assertNull(CosmeticsBackend.getActiveSkin(null));
            assertNull(CosmeticsBackend.getActiveCape(null));
            CosmeticsBackend.setActiveSkin(null, null);
            CosmeticsBackend.setActiveCape(null, null);
            CosmeticsBackend.clearActiveSkin(null);
            CosmeticsBackend.clearActiveCape(null);
        }, "Should not throw NullPointerException with null UUID");
    }
}
