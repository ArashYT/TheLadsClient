package com.thelads.smp.qa;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only frame-gap recorder (-Dladsqa.frames=1). A mixin calls {@link #frame()} at the end of every Minecraft#renderFrame; a daemon thread
 * (started in preLaunch, before the window exists) samples the game thread's stack whenever no frame was presented for 250 ms, so each gap
 * is written to ladsqa/frames.txt with the stack that blocked it. The first gap starts at JVM start.
 */
public final class FrameWatch implements PreLaunchEntrypoint {
    public static final boolean ON = Boolean.getBoolean("ladsqa.frames") || Boolean.getBoolean("ladsqa.world");
    static final long JVM_START_MS = ManagementFactory.getRuntimeMXBean().getStartTime();
    static final long NANO0 = System.nanoTime() - (System.currentTimeMillis() - JVM_START_MS) * 1_000_000L;
    static final long THRESHOLD_NS = Long.getLong("ladsqa.gapms", 250L) * 1_000_000L;
    static volatile long last = 0, frames = 0;
    static volatile String lastScreen = "";
    static long maxGapNs, gapsOver, totalFrames, firstFrame = -1;
    static final List<String> samples = new ArrayList<>();
    static Path dir;

    public static long ms(long nano) { return (nano - NANO0) / 1_000_000L; }

    @Override
    public void onPreLaunch() {
        if (!ON || FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        dir = FabricLoader.getInstance().getGameDir().resolve("ladsqa");
        try { Files.createDirectories(dir); Files.writeString(dir.resolve("frames.txt"), ""); } catch (Exception ignored) { }
        Thread t = new Thread(FrameWatch::watch, "ladsqa-framewatch");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(FrameWatch::summary));
    }

    static Thread gameThread() {
        Thread main = null;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().equals("Render thread")) return t;
            if (t.getName().equals("main")) main = t;
        }
        return main;
    }

    static void watch() {
        long lastSample = 0;
        while (true) {
            try { Thread.sleep(40); } catch (InterruptedException e) { return; }
            long now = System.nanoTime();
            long ref = last == 0 ? NANO0 : last;
            if (now - ref > THRESHOLD_NS && now - lastSample > 400_000_000L) {
                lastSample = now;
                Thread g = gameThread();
                if (g == null) continue;
                StackTraceElement[] st = g.getStackTrace();
                StringBuilder sb = new StringBuilder("  sample +" + ms(now) + " ms [" + g.getName() + "]");
                for (int i = 0; i < Math.min(st.length, 70); i++) sb.append("\n      ").append(st[i]);
                synchronized (samples) { if (samples.size() < 40) samples.add(sb.toString()); }
            }
        }
    }

    /** Called from the mixin at the end of every renderFrame (the frame was submitted to the window). */
    public static void frame() {
        long now = System.nanoTime();
        long ref = last == 0 ? NANO0 : last;
        long gap = now - ref;
        if (last == 0) { firstFrame = now; write("FIRSTFRAME +" + ms(now) + " ms after JVM start\n"); }
        last = now; frames++; totalFrames++;
        if (gap > maxGapNs && last != 0 && gap != now - NANO0) maxGapNs = gap;
        if (gap > THRESHOLD_NS) {
            if (!(totalFrames == 1)) gapsOver++;
            StringBuilder sb = new StringBuilder("GAP " + (gap / 1_000_000L) + " ms  from +" + ms(ref) + " to +" + ms(now) + " ms  screen=" + lastScreen + "\n");
            synchronized (samples) { for (String s : samples) sb.append(s).append('\n'); samples.clear(); }
            write(sb.toString());
        } else synchronized (samples) { samples.clear(); }
    }

    static int acks;

    /** Chunk-batch trace (test only), capped. */
    public static void ack(String what) {
        if (dir == null || acks > 3000) return;
        acks++;
        try { Files.writeString(dir.resolve("chunkack.txt"), String.format("+%d ms %s%n", ms(System.nanoTime()), what), StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception ignored) { }
    }

    static void write(String s) {
        if (dir == null) return;
        try { Files.writeString(dir.resolve("frames.txt"), s, StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception ignored) { }
    }

    static void summary() {
        write("SUMMARY frames=" + totalFrames + " maxGapAfterFirstFrame=" + (maxGapNs / 1_000_000L) + " ms gapsOver" + (THRESHOLD_NS / 1_000_000L) + "ms=" + gapsOver
                + " firstFrameAt=+" + (firstFrame < 0 ? -1 : ms(firstFrame)) + " ms\n");
    }
}
