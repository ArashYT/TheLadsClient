package com.thelads.core.perf;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (1.8.5 render capture, every adapter's RenderProbe): frame CPU time windows with the remakes off and on, the
 * BuiltInRender counters over each window, pixel and texel comparisons, PASS/FAILED checks and the results file.
 */
public final class RenderQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    public static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    public static int checks, failures;
    private static boolean recording;
    private static long phaseStart, frameNanos;
    private static long[] samples = new long[4096];
    private static int sampleCount;
    private static long kept0, culled0, lod0, copied0, uploaded0, animNanos0, animTicks0;

    private RenderQa() {}

    public static void reset() { RESULTS.clear(); checks = 0; failures = 0; recording = false; }

    /** Start of a timed phase of a frame (a frame may have several, e.g. 26.3's extract and render). */
    public static void phaseStart() { if (recording) phaseStart = System.nanoTime(); }

    /** End of a timed phase; {@code frameDone} closes the frame's sample. */
    public static void phaseEnd(boolean frameDone) {
        if (!recording) return;
        frameNanos += System.nanoTime() - phaseStart;
        if (!frameDone) return;
        if (sampleCount == samples.length) samples = Arrays.copyOf(samples, samples.length * 2);
        samples[sampleCount++] = frameNanos;
        frameNanos = 0;
    }

    public static void startWindow() {
        sampleCount = 0;
        frameNanos = 0;
        kept0 = BuiltInRender.quadsKept; culled0 = BuiltInRender.quadsCulled; lod0 = BuiltInRender.quadsLod;
        copied0 = BuiltInRender.framesCopied; uploaded0 = BuiltInRender.framesUploaded;
        animNanos0 = BuiltInRender.animationNanos; animTicks0 = BuiltInRender.animationTicks;
        recording = true;
    }

    public static void stop() { recording = false; }

    /** Closes the window: mean, median and p95 frame CPU ms, per-frame quad counts, frames copied/uploaded, animation cost per atlas tick. */
    @SuppressWarnings("unchecked")
    public static void endWindow(String scene, String mode) {
        recording = false;
        long[] s = Arrays.copyOf(samples, sampleCount);
        Arrays.sort(s);
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("mode", mode);
        w.put("frames", s.length);
        w.put("meanMs", round(s.length == 0 ? 0 : Arrays.stream(s).average().orElse(0) / 1e6));
        w.put("medianMs", round(s.length == 0 ? 0 : s[s.length / 2] / 1e6));
        w.put("p95Ms", round(s.length == 0 ? 0 : s[Math.min(s.length - 1, (int) (s.length * 0.95))] / 1e6));
        int frames = Math.max(1, s.length);
        w.put("quadsKeptPerFrame", (BuiltInRender.quadsKept - kept0) / frames);
        w.put("quadsCulledPerFrame", (BuiltInRender.quadsCulled - culled0) / frames);
        w.put("quadsLodPerFrame", (BuiltInRender.quadsLod - lod0) / frames);
        w.put("framesCopied", BuiltInRender.framesCopied - copied0);
        w.put("framesUploadedMapped", BuiltInRender.framesUploaded - uploaded0);
        long ticks = BuiltInRender.animationTicks - animTicks0;
        w.put("animationUsPerAtlasTick", round(ticks == 0 ? 0 : (BuiltInRender.animationNanos - animNanos0) / 1e3 / ticks));
        ((List<Object>) RESULTS.computeIfAbsent(scene, k -> new ArrayList<>())).add(w);
        LOGGER.info("Lads render 1.8.5 window {} {}: {}", scene, mode, w);
    }

    public static double round(double v) { return Math.round(v * 1000) / 1000.0; }

    public static int differing(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length) return Integer.MAX_VALUE;
        int count = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) count++;
        return count;
    }

    public static void check(boolean ok, String what) {
        if (ok) { checks++; LOGGER.info("Lads render 1.8.5 check PASS: {}", what); }
        else { failures++; LOGGER.error("Lads render 1.8.5 check FAILED: {}", what); }
    }

    /** Off, on and off-again frames of one still scene; {@code lod} allows LOD's at most 0.1% of pixels instead of none. */
    public static void compareShots(String scene, int[] off, int[] on, int[] again, boolean lod) {
        int changed = differing(off, on), control = differing(off, again);
        RESULTS.put(scene + "PixelsDifferingOffVsOn", changed);
        RESULTS.put(scene + "PixelsDifferingOffVsOff", control);
        check(control == 0, scene + ": the still scene draws the same twice with the remakes off (" + control + " pixels differ)");
        if (lod) {
            int limit = off == null ? 0 : off.length / 1000;
            check(changed <= limit, scene + ": LOD changes at most 0.1% of the far scene's pixels (" + changed + " differ, limit " + limit + ")");
        } else check(changed == 0, scene + ": the remakes on draw the still scene pixel for pixel as off (" + changed + " pixels differ)");
    }

    /** Two atlas readbacks of the same frames, texel for texel, plus texels still showing the poison colour in the first. */
    public static void compareAtlas(String what, int[][] first, int[][] second, int poison) {
        long differ = 0, left = 0, texels = 0;
        for (int level = 0; level < first.length; level++) {
            int[] p = first[level], q = second[level];
            texels += p.length;
            if (q == null || p.length != q.length) { differ += p.length; continue; }
            for (int i = 0; i < p.length; i++) {
                if (p[i] != q[i]) differ++;
                if (p[i] == poison) left++;
            }
        }
        RESULTS.put("atlas " + what, Map.of("texels", texels, "differing", differ, "leftoverPoison", left, "mipLevels", first.length));
        check(differ == 0, "block atlas " + what + ": same texels on all " + first.length + " mip levels (" + differ + " of " + texels + " differ)");
        check(left == 0, "block atlas " + what + ": every animated sprite was redrawn (" + left + " poisoned texels left)");
    }

    /** Writes results.json and logs END (or FAILED) with the check counts. */
    public static void finish(Path out) {
        RESULTS.put("checksPassed", checks);
        RESULTS.put("checksFailed", failures);
        try { Files.writeString(out.resolve("results.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS)); }
        catch (Exception failure) { failures++; LOGGER.error("Lads render 1.8.5 capture FAILED: results", failure); }
        if (failures == 0) LOGGER.info("Lads render 1.8.5 capture END: {} passed, 0 failed; results {}", checks, out.resolve("results.json"));
        else LOGGER.error("Lads render 1.8.5 capture FAILED: {} passed, {} failed; results {}", checks, failures, out.resolve("results.json"));
    }
}
