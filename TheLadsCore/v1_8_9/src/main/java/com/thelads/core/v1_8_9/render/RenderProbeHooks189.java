package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.RenderQa;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/** QA (1.8.5 render capture): what the render mixins hand to RenderProbe189 (in the feature package, beside CoreProbe). */
public final class RenderProbeHooks189 {
    /** Atlas animations held still while the probe compares frames. */
    public static boolean animationsHeld;
    /** Set by the probe: run after each frame (screenshots). */
    public static Runnable afterFrame;

    private RenderProbeHooks189() {}

    private static int[] queries;
    private static int gpuFrame;
    private static boolean timing;

    /** Frame start: CPU timing, and a GL timer query (two alternating, read a frame later) while a window records. */
    public static void frameStart() {
        RenderQa.phaseStart();
        if (!RenderQa.recording()) return;
        if (queries == null) queries = new int[]{GL15.glGenQueries(), GL15.glGenQueries()};
        int query = queries[gpuFrame & 1];
        if (gpuFrame >= 2 && GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_TRUE)
            RenderQa.gpuSample(GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT));
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
        timing = true;
    }

    public static void frameEnd() {
        RenderQa.phaseEnd(true);
        if (timing) { GL15.glEndQuery(GL33.GL_TIME_ELAPSED); gpuFrame++; timing = false; }
        Runnable after = afterFrame;
        if (after != null) after.run();
    }
}
