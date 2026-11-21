package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** LowLatencyTuner's wait maths against synthetic CPU and GPU frame series. */
class LowLatencyTunerTest {
    private static final long MS = 1_000_000L;

    private static long settle(LowLatencyTuner t, long gpu, long cpu, long margin, long interval, int frames) {
        long lead = 0;
        for (int i = 0; i < frames; i++) {
            lead = t.lead(gpu, cpu, gpu - cpu - margin);
            t.frame(interval, lead);
        }
        return lead;
    }

    @Test
    void gpuBoundGivesPositiveBoundedWait() {
        LowLatencyTuner t = new LowLatencyTuner();
        long lead = settle(t, 16 * MS, 4 * MS, 2 * MS, 16 * MS, 200);
        assertTrue(lead > 9 * MS && lead <= 10 * MS, "lead " + lead); // gpu - cpu - margin = 10 ms
        assertTrue(lead <= LowLatencyTuner.CAP_NS);
    }

    @Test
    void waitIsNeverOverOneFrameAt30Fps() {
        LowLatencyTuner t = new LowLatencyTuner();
        t.offsetMs(5);
        long lead = settle(t, 100 * MS, 1 * MS, 0, 100 * MS, 400);
        assertEquals(LowLatencyTuner.CAP_NS, lead, 1000);
    }

    @Test
    void cpuBoundWaitsZeroEvenWithOffset() {
        LowLatencyTuner t = new LowLatencyTuner();
        t.offsetMs(3);
        assertEquals(0, settle(t, 5 * MS, 12 * MS, 1 * MS, 12 * MS, 100));
        assertEquals(0, t.lead(5 * MS, 12 * MS, 5 * MS - 12 * MS - MS));
    }

    @Test
    void smoothingRampsInsteadOfJumping() {
        LowLatencyTuner t = new LowLatencyTuner();
        long first = t.lead(16 * MS, 4 * MS, 10 * MS);
        assertTrue(first > 0 && first < 2 * MS, "first " + first);
    }

    @Test
    void offsetShiftsTheWaitBothWays() {
        LowLatencyTuner plus = new LowLatencyTuner(), minus = new LowLatencyTuner();
        plus.offsetMs(2);
        minus.offsetMs(-2);
        long a = settle(plus, 16 * MS, 4 * MS, 2 * MS, 16 * MS, 300), b = settle(minus, 16 * MS, 4 * MS, 2 * MS, 16 * MS, 300);
        assertEquals(12 * MS, a, MS / 10);
        assertEquals(8 * MS, b, MS / 10);
    }

    @Test
    void fpsDropBacksOffAndRecovers() {
        LowLatencyTuner t = new LowLatencyTuner();
        settle(t, 16 * MS, 4 * MS, 2 * MS, 16 * MS, 100);
        assertEquals(1.0, t.scale(), 1e-9);
        // the wait now costs frames: the interval is 25% over the GPU frame time
        long lead = settle(t, 16 * MS, 4 * MS, 2 * MS, 20 * MS, 100);
        assertEquals(0.0, t.scale(), 1e-9);
        assertTrue(lead < MS / 2, "lead " + lead);
        settle(t, 16 * MS, 4 * MS, 2 * MS, 16 * MS, 150);
        assertTrue(t.scale() > 0.9, "scale " + t.scale());
    }

    @Test
    void debugLinesReportTheNumbers() {
        LowLatencyTuner t = new LowLatencyTuner();
        settle(t, 16 * MS, 4 * MS, 2 * MS, 16 * MS, 50);
        String text = String.join("\n", t.debugLines());
        assertTrue(text.contains("cpu 4.00 ms") && text.contains("gpu 16.00 ms"), text);
    }
}
