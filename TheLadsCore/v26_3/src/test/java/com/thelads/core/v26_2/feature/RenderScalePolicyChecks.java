package com.thelads.core.v26_2.feature;

/** Dependency-free checks for sizing and cadence; GPU/world verification lives in NativeRenderScaleProbe. */
public final class RenderScalePolicyChecks {
    private static int passed;
    public static void main(String[] args) {
        size(1920, 1080, .5, 16384, 960, 540);
        size(1920, 1080, 1, 16384, 1920, 1080);
        size(1920, 1080, 1.5, 16384, 2880, 1620);
        size(1920, 1080, 2, 16384, 3840, 2160);
        size(16384, 8192, 2, 16384, 16384, 8192);
        size(0, 0, .5, 4096, 1, 1);
        size(1001, 701, .5, 4096, 501, 351);
        size(1920, 1080, Double.NaN, 16384, 3840, 2160);
        require(settings(1, 100, false, 60, 50).maximumScale() == .5, "performance preset");
        require(settings(2, 100, false, 60, 50).maximumScale() == .75, "balanced preset");
        require(settings(3, 100, false, 60, 50).maximumScale() == .85, "quality preset");
        require(settings(4, 100, false, 60, 50).maximumScale() == 1.5, "supersampling preset");
        require(settings(2, 100, true, 60, 100).minimumScale() == .75, "minimum cannot exceed preset ceiling");
        var dynamic = settings(0, 100, true, 60, 50);
        var policy = new RenderScalePolicy();
        long time = 1_000_000_000L;
        require(policy.frame(dynamic, time, true) == 1, "dynamic starts at selected maximum");
        for (int i = 0; i < 180; i++) policy.frame(dynamic, time += 33_333_333L, true);
        double reduced = policy.frame(dynamic, time += 33_333_333L, true);
        require(reduced < .8 && reduced >= .5, "sustained missed frame budget lowers world resolution");
        for (int i = 0; i < 1500; i++) policy.frame(dynamic, time += 8_333_333L, true);
        require(Math.abs(policy.frame(dynamic, time += 8_333_333L, true) - 1) < .00001,
            "sustained spare frame budget recovers to selected maximum");
        for (int i = 0; i < 1000; i++) policy.frame(dynamic, time += 50_000_000L, true);
        require(policy.frame(dynamic, time += 50_000_000L, true) == .5, "dynamic never passes configured minimum");
        require(policy.frame(dynamic, time += 5_000_000_000L, true) == .5, "loading hitch does not alter resolution");
        var stable = new RenderScalePolicy();
        stable.frame(dynamic, time, true);
        for (int i = 0; i < 600; i++) stable.frame(dynamic, time += 16_666_667L, true);
        require(stable.frame(dynamic, time += 16_666_667L, true) == 1, "meeting target does not oscillate");
        require(policy.frame(settings(0, 150, false, 60, 50), time, true) == 1.5, "manual change applies immediately");
        require(policy.frame(settings(0, 150, true, 0, 50), time, true) == 1.5, "Unlimited uses selected fixed scale");
        require(policy.frame(dynamic, time, false) == 1, "inactive frame resets adaptive history");
        System.out.println("RenderScale policy checks passed: " + passed);
    }
    private static RenderScalePolicy.Settings settings(int preset, double scale, boolean dynamic, int target, double min) {
        return new RenderScalePolicy.Settings(true, preset, scale, false, dynamic, target, min);
    }
    private static void size(int w, int h, double scale, int limit, int expectedW, int expectedH) {
        var size = RenderScalePolicy.size(w, h, scale, limit);
        require(size.width() == expectedW && size.height() == expectedH, "texture dimensions " + w + "x" + h);
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        passed++;
    }
}
