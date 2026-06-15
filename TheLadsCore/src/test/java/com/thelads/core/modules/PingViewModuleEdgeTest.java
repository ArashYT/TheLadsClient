package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PingViewModuleEdgeTest {
    @Test
    public void testEdgeCases() {
        PingViewModule module = new PingViewModule();
        // Ping == 0
        assertEquals("0ms", module.getPingText(0));
        assertEquals(0xFF55FF55, module.getPingColor(0)); // 0 is green

        // Ping == 99
        assertEquals("99ms", module.getPingText(99));
        assertEquals(0xFF55FF55, module.getPingColor(99)); // < 100 is green

        // Ping == 100
        assertEquals("100ms", module.getPingText(100));
        assertEquals(0xFFFFFF55, module.getPingColor(100)); // 100 is yellow

        // Ping == 200
        assertEquals("200ms", module.getPingText(200));
        assertEquals(0xFFFFFF55, module.getPingColor(200)); // <= 200 is yellow

        // Ping == 201
        assertEquals("201ms", module.getPingText(201));
        assertEquals(0xFFFF5555, module.getPingColor(201)); // > 200 is red

        // Large ping
        assertEquals("9999ms", module.getPingText(9999));
        assertEquals(0xFFFF5555, module.getPingColor(9999));
    }
}
