package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DynamicFPSModuleStressTest {

    @Test
    public void testNegativeFramerateLimit() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(-1);
        module.onWindowFocusChanged(false);
        assertDoesNotThrow(() -> {
            int limit = module.getCurrentFramerateLimit();
            assertEquals(-1, limit);
        }, "Should handle negative limit without crashing");
    }
}
