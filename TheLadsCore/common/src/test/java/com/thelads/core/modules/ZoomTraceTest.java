package com.thelads.core.modules;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ZoomTraceTest {
    private static final long MS = 1_000_000L;

    @Test
    void realZoomAtAnIrregularFrameRatePasses() {
        ZoomModule zoom = new ZoomModule();
        zoom.setEnabled(true);
        ZoomTrace trace = new ZoomTrace();
        Random random = new Random(7);
        long t = 0;
        double fov = 70 * zoom.fovFactor(false, 1);
        zoom.key(true);
        trace.phase("in", fov, 70 * ZoomModule.DEFAULT_ZOOM);
        for (int i = 0; i < 200; i++) {
            t += (2 + random.nextInt(40)) * MS; // 25-500 FPS, changing every frame
            fov = 70 * zoom.fovFactor(false, t);
            zoom.fovFactor(true, t + MS); // the hand pass also asks, a moment later
            trace.frame(fov, t);
        }
        zoom.key(false);
        trace.phase("out", fov, 70);
        for (int i = 0; i < 200; i++) {
            t += (2 + random.nextInt(40)) * MS;
            fov = 70 * zoom.fovFactor(false, t);
            trace.frame(fov, t);
        }
        trace.finish();
        assertEquals(java.util.List.of(), trace.failures(), trace.summary());
        assertTrue(trace.csv().startsWith("phase,ms,frame_ms,fov,expected_fov\nin,0.000,"));
    }

    @Test
    void aJumpOrOvershootFails() {
        ZoomTrace trace = new ZoomTrace();
        trace.phase("in", 70, 17.5);
        trace.frame(40, 10 * MS);
        trace.frame(15, 20 * MS); // past the goal
        trace.frame(30, 30 * MS); // back out
        trace.finish();
        assertFalse(trace.failures().isEmpty());
        assertTrue(trace.failures().stream().anyMatch(f -> f.contains("past it")), trace.failures().toString());
    }
}
