package com.thelads.core.perf;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * Executes mod discovery tasks concurrently and caches known non-mod library files
 * to accelerate startup on multi-core systems (PolyPatcher parity).
 */
public final class AsyncModScanner {
    private static final Map<String, Boolean> KNOWN_NON_MODS = new ConcurrentHashMap<>();

    private AsyncModScanner() {}

    public static String fileFingerprint(File file) {
        if (file == null || !file.exists()) return "";
        return file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length();
    }

    public static boolean isKnownNonMod(File file) {
        if (file == null) return false;
        return Boolean.TRUE.equals(KNOWN_NON_MODS.get(fileFingerprint(file)));
    }

    public static void markNonMod(File file) {
        if (file != null && file.exists()) {
            KNOWN_NON_MODS.put(fileFingerprint(file), Boolean.TRUE);
        }
    }

    public static void clearCache() {
        KNOWN_NON_MODS.clear();
    }

    /**
     * Maps candidates in parallel using ForkJoinPool, preserving input list order.
     */
    public static <T, R> List<R> mapParallel(List<T> items, Function<T, R> mapper) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        if (items.size() == 1 || Runtime.getRuntime().availableProcessors() <= 1) {
            List<R> results = new ArrayList<>(items.size());
            for (T item : items) results.add(mapper.apply(item));
            return results;
        }

        try {
            return ForkJoinPool.commonPool().submit(() ->
                items.parallelStream().map(mapper).toList()
            ).get();
        } catch (Exception e) {
            // Fallback to sequential on any thread pool interruption
            List<R> results = new ArrayList<>(items.size());
            for (T item : items) results.add(mapper.apply(item));
            return results;
        }
    }
}
