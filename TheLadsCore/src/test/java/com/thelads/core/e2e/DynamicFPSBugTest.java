package com.thelads.core.e2e;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DynamicFPSBugTest {
    @Test
    public void testThrottlingWhenUnlimited() {
        com.thelads.core.modules.DynamicFPSModule module = new com.thelads.core.modules.DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(0); // 0 means unlimited
        module.onWindowFocusChanged(false);
        // It should throttle to 15!
        assertEquals(15, module.getCurrentFramerateLimit(), "When unlimited (0), unfocusing should throttle to 15, not stay at 0 (unlimited)!");
    }
}
