package com.thelads.core.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Sizing and cadence only; GPU/world verification lives in the adapters' NativeRenderScaleProbe. */
class RenderScalePolicyTest {
    private static RenderScalePolicy.Settings settings(int preset, double scale, boolean dynamic, int target, double min) {
        return new RenderScalePolicy.Settings(true, preset, scale, RenderScalePolicy.LINEAR, dynamic, target, min);
    }
    private static void size(int w, int h, double scale, int limit, int expectedW, int expectedH) {
        var size = RenderScalePolicy.size(w, h, scale, limit);
        assertEquals(new RenderScalePolicy.Size(expectedW, expectedH), size, "texture dimensions " + w + "x" + h + " at " + scale);
    }
    @Test void textureSizeFollowsScaleWithinLimits() {
        size(1920, 1080, .5, 16384, 960, 540);
        size(1920, 1080, 1, 16384, 1920, 1080);
        size(1920, 1080, 1.5, 16384, 2880, 1620);
        size(1920, 1080, 2, 16384, 3840, 2160);
        size(16384, 8192, 2, 16384, 16384, 8192);
        size(0, 0, .5, 4096, 1, 1);
        size(1001, 701, .5, 4096, 501, 351);
        size(1920, 1080, Double.NaN, 16384, 3840, 2160);
    }
    @Test void presetsSetTheCeilingAndBoundTheMinimum() {
        assertEquals(.5, settings(1, 100, false, 60, 50).maximumScale(), "performance preset");
        assertEquals(.75, settings(2, 100, false, 60, 50).maximumScale(), "balanced preset");
        assertEquals(.85, settings(3, 100, false, 60, 50).maximumScale(), "quality preset");
        assertEquals(1.5, settings(4, 100, false, 60, 50).maximumScale(), "supersampling preset");
        assertEquals(.75, settings(2, 100, true, 60, 100).minimumScale(), "minimum cannot exceed preset ceiling");
    }
    @Test void dynamicScaleAdaptsToSustainedFrameTimesOnly() {
        var dynamic = settings(0, 100, true, 60, 50);
        var policy = new RenderScalePolicy();
        long time = 1_000_000_000L;
        assertEquals(1, policy.frame(dynamic, time, true), "dynamic starts at selected maximum");
        for (int i = 0; i < 180; i++) policy.frame(dynamic, time += 33_333_333L, true);
        double reduced = policy.frame(dynamic, time += 33_333_333L, true);
        assertTrue(reduced < .8 && reduced >= .5, "sustained missed frame budget lowers world resolution");
        for (int i = 0; i < 1500; i++) policy.frame(dynamic, time += 8_333_333L, true);
        assertEquals(1, policy.frame(dynamic, time += 8_333_333L, true), .00001, "sustained spare frame budget recovers to selected maximum");
        for (int i = 0; i < 1000; i++) policy.frame(dynamic, time += 50_000_000L, true);
        assertEquals(.5, policy.frame(dynamic, time += 50_000_000L, true), "dynamic never passes configured minimum");
        assertEquals(.5, policy.frame(dynamic, time += 5_000_000_000L, true), "loading hitch does not alter resolution");
        var stable = new RenderScalePolicy();
        stable.frame(dynamic, time, true);
        for (int i = 0; i < 600; i++) stable.frame(dynamic, time += 16_666_667L, true);
        assertEquals(1, stable.frame(dynamic, time += 16_666_667L, true), "meeting target does not oscillate");
        assertEquals(1.5, policy.frame(settings(0, 150, false, 60, 50), time, true), "manual change applies immediately");
        assertEquals(1.5, policy.frame(settings(0, 150, true, 0, 50), time, true), "Unlimited uses selected fixed scale");
        assertEquals(1, policy.frame(dynamic, time, false), "inactive frame resets adaptive history");
    }
}
