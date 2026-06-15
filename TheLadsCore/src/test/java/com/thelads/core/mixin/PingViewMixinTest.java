package com.thelads.core.mixin;

import com.thelads.core.modules.PingViewModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PingViewMixinTest {

    @Test
    public void testNegativePingHandling() {
        PingViewModule module = new PingViewModule();
        
        // For negative ping, we expect "???" and red color (0xFFFF5555)
        assertEquals("???", module.getPingText(-1));
        assertEquals(0xFFFF5555, module.getPingColor(-1));
    }

    @Test
    public void testAlphaRenderingColor() {
        PingViewModule module = new PingViewModule();
        
        // Green color check for low ping
        int greenColor = module.getPingColor(50);
        assertEquals(0xFF55FF55, greenColor, "Color must have full alpha (0xFF...)");
        
        // Yellow color check for medium ping
        int yellowColor = module.getPingColor(150);
        assertEquals(0xFFFFFF55, yellowColor, "Color must have full alpha (0xFF...)");
        
        // Red color check for high ping
        int redColor = module.getPingColor(250);
        assertEquals(0xFFFF5555, redColor, "Color must have full alpha (0xFF...)");
    }

    @Test
    public void testPingTextFormatting() {
        PingViewModule module = new PingViewModule();
        assertEquals("100ms", module.getPingText(100));
        assertEquals("50ms", module.getPingText(50));
    }
}
