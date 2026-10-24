package com.thelads.core.client.rewind;

import com.thelads.core.modules.RewindModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstantClipBufferTest {
    @BeforeEach
    void setUp() {
        InstantClipBuffer.clear();
    }

    @Test
    void testDefaults() {
        RewindModule mod = new RewindModule();
        assertFalse(mod.isEnabled());
        assertEquals(30, mod.bufferDuration.getIntValue());
        assertTrue(mod.notifyOnClip.get());
        assertFalse(mod.recordMic.get());
    }

    @Test
    void testClipWindowCalculation() {
        long now = 1000000L;
        var window = InstantClipBuffer.triggerClip(now, 30);
        assertEquals(1000000L, window.endEpochMs());
        assertEquals(1000000L - 30000L, window.startEpochMs());
        assertEquals(30, window.durationSeconds());
    }

    @Test
    void testBufferSampleRecording() {
        assertEquals(0, InstantClipBuffer.getSampleCount());
        InstantClipBuffer.recordTimestamp(100L);
        InstantClipBuffer.recordTimestamp(200L);
        assertEquals(2, InstantClipBuffer.getSampleCount());
        InstantClipBuffer.clear();
        assertEquals(0, InstantClipBuffer.getSampleCount());
    }
}
