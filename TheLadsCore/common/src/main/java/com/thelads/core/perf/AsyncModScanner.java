package com.thelads.core.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Executes mod discovery tasks concurrently to accelerate startup on multi-core systems (PolyPatcher parity).
 */
public final class AsyncModScanner {
    private AsyncModScanner() {}

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
                items.parallelStream().map(mapper).collect(Collectors.toList())
            ).get();
        } catch (Exception e) {
            // Fallback to sequential on any thread pool interruption
            List<R> results = new ArrayList<>(items.size());
            for (T item : items) results.add(mapper.apply(item));
            return results;
        }
    }
}
