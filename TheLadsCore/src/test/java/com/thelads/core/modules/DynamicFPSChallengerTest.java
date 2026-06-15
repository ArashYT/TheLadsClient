package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DynamicFPSChallengerTest {

    @Test
    public void testVsyncLimit() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(0); // 0 means VSync in MC options often
        module.onWindowFocusChanged(false);
        
        assertEquals(15, module.getCurrentFramerateLimit(), "When unfocused with 0 (VSync), limit should be 15");
    }

    @Test
    public void testHighLimit() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(260); // 260 means Unlimited
        module.onWindowFocusChanged(false);
        
        assertEquals(15, module.getCurrentFramerateLimit(), "When unfocused with high limit, limit should be capped at 15");
    }

    @Test
    public void testLowLimit() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(10); // user wants 10 FPS normally
        module.onWindowFocusChanged(false);
        
        assertEquals(10, module.getCurrentFramerateLimit(), "When unfocused with limit < 15, should maintain user's lower limit");
    }

    @Test
    public void testNegativeLimitEdgeCase() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(-10);
        module.onWindowFocusChanged(false);
        
        assertEquals(-10, module.getCurrentFramerateLimit(), "Edge case: negative limit is respected (min(15, -10) = -10)");
    }

    @Test
    public void testFocusedReturnsOriginal() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(120);
        module.onWindowFocusChanged(true);
        
        assertEquals(120, module.getCurrentFramerateLimit(), "When focused, should return original limit");
    }

    @Test
    public void testDisabledReturnsOriginal() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(false);
        module.setOriginalFramerateLimit(260);
        module.onWindowFocusChanged(false);
        
        assertEquals(260, module.getCurrentFramerateLimit(), "When disabled, unfocused should not affect limit");
    }
}
