package com.thelads.ladsapi.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FabricPermissionsTest {

    @Test
    public void testFabricPermissionsApiDetection() {
        // In plain unit test environment without fabric-permissions-api,
        // it must safely detect absence and not crash or throw ClassNotFoundException
        assertDoesNotThrow(() -> {
            boolean hasApi = FabricPermissions.hasFabricPermissionsApi();
            assertFalse(hasApi, "fabric-permissions-api should not be present in standalone unit tests");
        });
    }
}
