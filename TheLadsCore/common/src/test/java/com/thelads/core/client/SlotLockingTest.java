package com.thelads.core.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SlotLockingTest {
    @BeforeEach
    public void setup() {
        SlotLocking.clearLocks();
        com.thelads.core.config.Module mod = com.thelads.core.config.ModuleManager.getInstance().getModule("SlotLocking");
        if (mod != null) mod.setEnabled(true);
    }

    @Test
    public void testSlotLocking() {
        SlotLocking.setSlotLocked(5, true);
        assertTrue(SlotLocking.isSlotLocked(5));
        assertFalse(SlotLocking.canDropSlot(5));

        SlotLocking.toggleLock(5);
        assertFalse(SlotLocking.isSlotLocked(5));
        assertTrue(SlotLocking.canDropSlot(5));
    }
}
