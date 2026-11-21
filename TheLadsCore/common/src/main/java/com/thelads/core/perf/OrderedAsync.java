package com.thelads.core.perf;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Ordered off-thread work: one daemon worker runs the pure part of each job in submit order and
 * posts the result to the main executor from that same worker, so results are applied in exactly
 * the order they were submitted. No Minecraft types. The apply callback receives null when the work
 * threw or when the job is stale (a {@link #invalidate()} happened after it was submitted), so the
 * caller can fall back to the plain synchronous path and never loses a job.
 * {@link #cancelPending()} drops queued jobs outright (their apply callback is never called).
 */
public final class OrderedAsync {
    private final ExecutorService worker;
    private final AtomicBoolean logged = new AtomicBoolean();
    private final Consumer<Throwable> onError;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile int generation;
    private volatile int dropEpoch;

    public OrderedAsync(String threadName, Consumer<Throwable> onError) {
        this.onError = onError;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
    }

    /** Marks every job submitted so far as stale: it is still applied in order, but with a null result. */
    public void invalidate() {
        generation++;
    }

    /** Drops every job submitted so far without calling its apply callback (vanilla "clear" semantics). */
    public void cancelPending() {
        dropEpoch++;
        generation++;
    }

    /** Jobs submitted whose apply step has not run yet. */
    public int pending() {
        return pending.get();
    }

    public int generation() {
        return generation;
    }

    /** Runs work on the worker, then applyOnMain on the main executor, in submit order. Call from the main thread. */
    public <T> void submit(Callable<T> work, Consumer<T> applyOnMain, Executor main) {
        final int gen = generation;
        final int epoch = dropEpoch;
        pending.incrementAndGet();
        try {
            worker.execute(() -> {
                T result = null;
                try {
                    result = work.call();
                } catch (Throwable t) {
                    if (logged.compareAndSet(false, true) && onError != null) onError.accept(t);
                }
                final T r = result;
                main.execute(() -> {
                    try {
                        if (epoch != dropEpoch) return;
                        applyOnMain.accept(gen == generation ? r : null);
                    } finally {
                        pending.decrementAndGet();
                    }
                });
            });
        } catch (RuntimeException rejected) {
            main.execute(() -> {
                try {
                    if (epoch == dropEpoch) applyOnMain.accept(null);
                } finally {
                    pending.decrementAndGet();
                }
            });
        }
    }
}
