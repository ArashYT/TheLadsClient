package com.thelads.core.client.benchmark;

import net.minecraft.client.Minecraft;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BenchmarkTracker {
    public static final boolean ENABLED = "true".equals(System.getProperty("thelads.benchmark"));

    public static long earlyStartTime = 0;
    public static long minecraftInitTime = 0;
    public static long resourceLoadStartTime = 0;
    public static long resourceLoadEndTime = 0;
    public static long titleScreenReadyTime = 0;
    public static long joinStartTime = 0;
    public static long joinEndTime = 0;

    // Measurement phase state
    private static long spawnTime = 0;
    private static long lastRenderTime = 0;
    private static final List<Long> frameTimes = new ArrayList<>();
    private static int maxLoadedChunks = 0;
    private static int totalCompiledChunks = 0;
    private static boolean completed = false;

    public static void setEarlyStartTime(long time) {
        if (!ENABLED) return;
        earlyStartTime = time;
    }

    public static void setMinecraftInitTime(long time) {
        if (!ENABLED) return;
        minecraftInitTime = time;
    }

    public static void setResourceLoadStartTime(long time) {
        if (!ENABLED) return;
        resourceLoadStartTime = time;
    }

    public static void setResourceLoadEndTime(long time) {
        if (!ENABLED) return;
        resourceLoadEndTime = time;
    }

    public static void setTitleScreenReadyTime(long time) {
        if (!ENABLED) return;
        titleScreenReadyTime = time;
    }

    public static void setJoinStartTime(long time) {
        if (!ENABLED) return;
        joinStartTime = time;
    }

    public static void setJoinEndTime(long time) {
        if (!ENABLED) return;
        joinEndTime = time;
        if (spawnTime == 0) {
            spawnTime = System.nanoTime();
        }
    }

    public static synchronized void incrementCompiledChunks() {
        if (!ENABLED) return;
        totalCompiledChunks++;
    }

    public static void onRenderLevelHead() {
        if (!ENABLED || completed) return;
        long now = System.nanoTime();
        if (spawnTime != 0) {
            long elapsedNanos = now - spawnTime;
            // Warmup is 5 seconds, Measurement is 15 seconds, total 20 seconds
            if (elapsedNanos >= 5_000_000_000L && elapsedNanos < 20_000_000_000L) {
                if (lastRenderTime != 0) {
                    long frameTime = now - lastRenderTime;
                    synchronized (frameTimes) {
                        frameTimes.add(frameTime);
                    }
                }
                // Record logical chunk count at each frame in the measurement phase
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null && mc.level.getChunkSource() != null) {
                        int currentLoaded = mc.level.getChunkSource().getLoadedChunksCount();
                        if (currentLoaded > maxLoadedChunks) {
                            maxLoadedChunks = currentLoaded;
                        }
                    }
                } catch (Exception e) {
                    // Ignore if level is not ready yet
                }
            } else if (elapsedNanos >= 20_000_000_000L) {
                completed = true;
                completeBenchmark();
            }
        }
        lastRenderTime = now;
    }

    private static void completeBenchmark() {
        long readyTime = (titleScreenReadyTime > 0) ? titleScreenReadyTime : joinStartTime;
        double startupTimeMs = (readyTime > earlyStartTime) ? (readyTime - earlyStartTime) / 1_000_000.0 : 0;
        double resourceLoadTimeMs = (resourceLoadEndTime > resourceLoadStartTime) ? (resourceLoadEndTime - resourceLoadStartTime) / 1_000_000.0 : 0;
        double worldJoinTimeMs = (joinEndTime > joinStartTime) ? (joinEndTime - joinStartTime) / 1_000_000.0 : 0;

        double averageFps = 0;
        double low1Fps = 0;
        double low01Fps = 0;

        List<Long> frames;
        synchronized (frameTimes) {
            frames = new ArrayList<>(frameTimes);
        }

        if (!frames.isEmpty()) {
            long totalNanos = 0;
            List<Double> frameRates = new ArrayList<>();
            for (long frameTimeNanos : frames) {
                totalNanos += frameTimeNanos;
                double fps = 1_000_000_000.0 / frameTimeNanos;
                frameRates.add(fps);
            }
            averageFps = ((double) frames.size()) / (totalNanos / 1_000_000_000.0);
            
            Collections.sort(frameRates);
            int size = frameRates.size();
            
            // 1% Low FPS (1st percentile)
            int index1 = (int) (size * 0.01);
            low1Fps = frameRates.get(Math.max(0, index1));

            // 0.1% Low FPS (0.1st percentile)
            int index01 = (int) (size * 0.001);
            low01Fps = frameRates.get(Math.max(0, index01));
        }

        String json = String.format(
            "{\n" +
            "  \"startup_time_ms\": %.2f,\n" +
            "  \"startupTimeMs\": %.2f,\n" +
            "  \"resource_load_time_ms\": %.2f,\n" +
            "  \"resourceLoadTimeMs\": %.2f,\n" +
            "  \"world_join_time_ms\": %.2f,\n" +
            "  \"worldJoinTimeMs\": %.2f,\n" +
            "  \"average_fps\": %.2f,\n" +
            "  \"averageFps\": %.2f,\n" +
            "  \"1_percent_low_fps\": %.2f,\n" +
            "  \"onePercentLowFps\": %.2f,\n" +
            "  \"0_1_percent_low_fps\": %.2f,\n" +
            "  \"zeroPointOnePercentLowFps\": %.2f,\n" +
            "  \"max_loaded_chunks\": %d,\n" +
            "  \"maxLoadedChunks\": %d,\n" +
            "  \"total_compiled_chunks\": %d,\n" +
            "  \"totalCompiledChunks\": %d\n" +
            "}\n",
            startupTimeMs, startupTimeMs,
            resourceLoadTimeMs, resourceLoadTimeMs,
            worldJoinTimeMs, worldJoinTimeMs,
            averageFps, averageFps,
            low1Fps, low1Fps,
            low01Fps, low01Fps,
            maxLoadedChunks, maxLoadedChunks,
            totalCompiledChunks, totalCompiledChunks
        );

        writeJsonToFile(new File("benchmark_results.json"), json);
        writeJsonToFile(new File("C:\\The Lads Client\\benchmark_results.json"), json);

        System.out.println("=== BENCHMARK COMPLETED ===");
        System.out.println(json);
        System.out.println("===========================");

        try {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(mc::stop);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    private static void writeJsonToFile(File file, String json) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
