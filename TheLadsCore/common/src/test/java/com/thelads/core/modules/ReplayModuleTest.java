package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayModuleTest {
    @Test
    void testDefaults() {
        ReplayModule mod = new ReplayModule();
        assertFalse(mod.isEnabled());
        assertTrue(mod.recordingIndicator.get());
        assertFalse(mod.autoRecord.get());
    }
}
