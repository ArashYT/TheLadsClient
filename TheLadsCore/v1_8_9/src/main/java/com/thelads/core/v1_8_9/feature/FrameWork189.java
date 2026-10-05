package com.thelads.core.v1_8_9.feature;

/**
 * QA only (Probe173Perf): each frame's work, from the start of Minecraft.runGameLoop to just before its Display.update (MinecraftMixin),
 * so neither the buffer swap nor the frame-limit sleep counts: the ms of work per frame under the 60 FPS QA cap. Costs one null
 * check per frame while nothing records.
 */
public final class FrameWork189 {
    static long[] times;
    static int count;
    private static long start;

    private FrameWork189() {}

    public static void start() {
        if (times != null) start = System.nanoTime();
    }

    public static void end() {
        if (times != null && start != 0 && count < times.length) times[count++] = System.nanoTime() - start;
        start = 0;
    }

    /** Records the next {@code frames} frames (0 stops). */
    static void record(int frames) {
        times = frames > 0 ? new long[frames] : null;
        count = 0;
        start = 0;
    }
}
