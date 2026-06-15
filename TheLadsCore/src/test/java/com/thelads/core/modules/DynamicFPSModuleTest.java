package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DynamicFPSModuleTest {
    @Test
    public void testFramerateThrottlingEdgeCases() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);

        // Case 1: Normal limit above 15
        module.setOriginalFramerateLimit(60);
        module.onWindowFocusChanged(false);
        assertEquals(15, module.getCurrentFramerateLimit(), "Framerate should be throttled to 15 when original is 60");

        // Case 2: Limit below 15 (should not increase)
        module.setOriginalFramerateLimit(10);
        module.onWindowFocusChanged(false);
        assertEquals(10, module.getCurrentFramerateLimit(), "Framerate should not increase to 15 if original was 10");

        // Case 3: Unlimited limit (represented as 0)
        module.setOriginalFramerateLimit(0);
        module.onWindowFocusChanged(false);
        assertEquals(15, module.getCurrentFramerateLimit(), "Framerate should be throttled to 15 when original is 0 (unlimited)");
    }
}
