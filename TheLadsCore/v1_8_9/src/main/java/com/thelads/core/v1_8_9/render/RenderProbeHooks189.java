package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.RenderQa;

/** QA (1.8.5 render capture): what the render mixins hand to RenderProbe189 (in the feature package, beside CoreProbe). */
public final class RenderProbeHooks189 {
    /** Atlas animations held still while the probe compares frames. */
    public static boolean animationsHeld;
    /** Set by the probe: run after each frame (screenshots). */
    public static Runnable afterFrame;

    private RenderProbeHooks189() {}

    public static void frameStart() { RenderQa.phaseStart(); }

    public static void frameEnd() {
        RenderQa.phaseEnd(true);
        Runnable after = afterFrame;
        if (after != null) after.run();
    }
}
