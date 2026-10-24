package com.thelads.core.v1_8_9.perf;

import com.thelads.core.perf.Perf189Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityInstancing189Test {
    @Test
    public void testBufferConstants() {
        assertEquals(1024, EntityInstancing189.MAX_INSTANCES);
        assertEquals(16, EntityInstancing189.FLOATS_PER_INSTANCE);
    }

    @Test
    public void testAvailabilityWithSetting() {
        Perf189Settings.gpuEntityInstancing = false;
        assertFalse(EntityInstancing189.isAvailable());

        EntityInstancing189.beginPass();
        assertEquals(0, EntityInstancing189.getQueuedLivingCount());
        assertEquals(0, EntityInstancing189.getQueuedArmorCount());

        EntityInstancing189.flush();
        assertEquals(0, EntityInstancing189.getQueuedLivingCount());
    }
}
