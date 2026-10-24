package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrossVersionModuleTest {
    @Test
    void testDefaults() {
        CrossVersionModule mod = new CrossVersionModule();
        assertFalse(mod.isEnabled(), "Gated module must default off");
        assertEquals("Auto", mod.targetProtocol.getValue());
    }
}
