package com.thelads.core.mixin;

import com.thelads.core.modules.PingViewModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PingViewStressTest {

    @Test
    public void testAllPossiblePingValues() {
        PingViewModule module = new PingViewModule();
        
        // Test a wide range of values, including extreme negative and positive
        int[] testValues = {
            Integer.MIN_VALUE, -9999, -1, 0, 1, 50, 99, 100, 150, 200, 201, 999, 1000, 99999, Integer.MAX_VALUE
        };
        
        for (int ping : testValues) {
            String text = module.getPingText(ping);
            int color = module.getPingColor(ping);
            
            assertNotNull(text, "Text should never be null");
            
            if (ping < 0) {
                assertEquals("???", text);
                assertEquals(0xFFFF5555, color);
            } else if (ping < 100) {
                assertEquals(ping + "ms", text);
                assertEquals(0xFF55FF55, color);
            } else if (ping <= 200) {
                assertEquals(ping + "ms", text);
                assertEquals(0xFFFFFF55, color);
            } else {
                assertEquals(ping + "ms", text);
                assertEquals(0xFFFF5555, color);
            }
        }
    }
}
