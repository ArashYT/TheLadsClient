package com.thelads.core.client.breeding;

import com.thelads.core.modules.BreedingTimerModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BreedingTimerTest {
    @Test
    void testDefaults() {
        BreedingTimerModule mod = new BreedingTimerModule();
        assertFalse(mod.isEnabled(), "Gated module must default off");
        assertTrue(mod.showSeconds.get());
        assertEquals(16, mod.range.getIntValue());
    }

    @Test
    void testFormatTimer() {
        assertNull(BreedingTimerHelper.formatTimer(0));
        assertEquals("Ready: 5:00", BreedingTimerHelper.formatTimer(6000));
        assertEquals("Baby: 1:00", BreedingTimerHelper.formatTimer(-1200));
        assertEquals("Ready: 0:45", BreedingTimerHelper.formatTimer(900));
    }
}
