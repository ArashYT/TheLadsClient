package com.thelads.core.v1_8_9.render;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * QA (benchmarks, -Dlads.gpuPhases=1): how long the GPU spends on each part of a frame. A GL timestamp is written at the start and
 * end of each phase (the GpuPhases*ProbeMixin189 hooks), read four frames later, and the averages are logged every 10 s as
 * "Lads GPU phases". The deltas are GPU execution time while the GPU is the bottleneck (a GPU that waits on the CPU adds the wait).
 * The CPU time between the same marks (System.nanoTime: the game's and the driver's work to submit the phase) is logged beside it
 * as "Lads CPU phases"; where the CPU frame is longer than the GPU's, the GPU waits ("swap" on the GPU line grows).
 * Off for players: one static check per hook.
 */
public final class GpuPhases189 {
    public static final boolean ON = Boolean.getBoolean("lads.gpuPhases");
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    public static final int FRAME = 0, WORLD = 1, SKY = 2, TERRAIN = 3, ENTITIES = 4, CLOUDS = 5, HAND = 6, HUD = 7, BLIT = 8, SWAP = 9, LADS_HUD = 10, VIGNETTE = 11;
    /** CPU only (no GPU timestamp): the HUD's parts. */
    public static final int CROSSHAIR = 12, HOTBAR = 13, BARS = 14, CHAT = 15, HUD_TEXT = 16, EVENTS = 17, LADS_ALL = 18, QA_BAR = 19;
    private static final int GPU_PHASES = 12;
    private static final String[] NAMES = {"frame", "world", "sky", "terrain", "entities", "clouds", "hand", "hud", "blit", "swap", "ladshud", "vignette",
        "crosshair", "hotbar", "bars", "chat", "hudtext", "events", "ladsall", "qabar"};
    private static final int SLOTS = 4, MARKS = 96;
    private static int[][] queries;
    /** Per slot: phase << 1 | (1 = end) of each mark written. */
    private static final int[][] what = new int[SLOTS][MARKS];
    private static final int[] count = new int[SLOTS];
    private static final long[] open = new long[NAMES.length];
    private static final long[] total = new long[NAMES.length];
    private static final long[] cpuOpen = new long[NAMES.length], cpuTotal = new long[NAMES.length];
    private static int cpuFrames;
    private static int frame, frames;
    private static long lastLog;
    private static boolean failed;

    private GpuPhases189() {}

    public static void begin(int phase) { mark(phase << 1); }

    public static void end(int phase) { mark(phase << 1 | 1); }

    private static void mark(int code) {
        if (failed || queries == null) return;
        long now = System.nanoTime();
        if ((code & 1) == 0) cpuOpen[code >> 1] = now;
        else if (cpuOpen[code >> 1] != 0) {
            cpuTotal[code >> 1] += now - cpuOpen[code >> 1];
            cpuOpen[code >> 1] = 0;
        }
        int slot = frame % SLOTS;
        if ((code >> 1) >= GPU_PHASES || count[slot] >= MARKS) return;
        GL33.glQueryCounter(queries[slot][count[slot]], GL33.GL_TIMESTAMP);
        what[slot][count[slot]++] = code;
    }

    /** Head of the game loop: reads the slot written SLOTS frames ago, then starts this frame's. */
    public static void frameStart() {
        if (failed) return;
        try {
            if (queries == null) {
                queries = new int[SLOTS][MARKS];
                for (int[] slot : queries) for (int i = 0; i < MARKS; i++) slot[i] = GL15.glGenQueries();
            } else end(SWAP); // in the previous frame's slot: its swap ends where this frame starts
            frame++;
            cpuFrames++;
            int slot = frame % SLOTS;
            if (frame > SLOTS && count[slot] > 0) read(slot);
            count[slot] = 0;
            begin(FRAME);
        } catch (Throwable t) {
            failed = true;
            LOGGER.warn("Lads GPU phases off: " + t);
        }
    }

    private static void read(int slot) {
        int n = count[slot];
        if (GL15.glGetQueryObjecti(queries[slot][n - 1], GL15.GL_QUERY_RESULT_AVAILABLE) != 1) return; // never stall the frame
        java.util.Arrays.fill(open, -1);
        for (int i = 0; i < n; i++) {
            long t = GL33.glGetQueryObjecti64(queries[slot][i], GL15.GL_QUERY_RESULT);
            int phase = what[slot][i] >> 1;
            if ((what[slot][i] & 1) == 0) open[phase] = t;
            else if (open[phase] >= 0) {
                total[phase] += t - open[phase];
                open[phase] = -1;
            }
        }
        frames++;
        long now = System.nanoTime();
        if (lastLog == 0) lastLog = now;
        if (now - lastLog < 10_000_000_000L) return;
        StringBuilder line = new StringBuilder("Lads GPU phases (us per frame, ").append(frames).append(" frames):");
        for (int p = 0; p < GPU_PHASES; p++) {
            line.append(' ').append(NAMES[p]).append(' ').append(String.format(java.util.Locale.ROOT, "%.1f", total[p] / 1000.0 / frames));
            total[p] = 0;
        }
        LOGGER.info(line.toString());
        StringBuilder cpu = new StringBuilder("Lads CPU phases (us per frame, ").append(cpuFrames).append(" frames):");
        for (int p = 0; p < NAMES.length; p++) {
            cpu.append(' ').append(NAMES[p]).append(' ').append(String.format(java.util.Locale.ROOT, "%.1f", cpuTotal[p] / 1000.0 / cpuFrames));
            cpuTotal[p] = 0;
        }
        LOGGER.info(cpu.toString());
        cpuFrames = 0;
        frames = 0;
        lastLog = now;
    }

    /** Head of updateDisplay: the frame's work is done, the swap (and the compositor's copy) follows. */
    public static void beforeSwap() {
        end(FRAME);
        begin(SWAP);
    }
}
