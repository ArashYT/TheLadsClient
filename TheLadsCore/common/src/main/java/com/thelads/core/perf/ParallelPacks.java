package com.thelads.core.perf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Parallel server resource pack downloads (the Lads remake of MultiThreadedFileDownload), game-free logic: runs the entries of one
 * batch up to {@link #MAX_AT_ONCE} at a time, and merges the per-pack progress callbacks into the single serial-looking stream the
 * vanilla download toast expects. Always on; {@code -Dlads.parallelpacks=false} turns it off.
 */
public final class ParallelPacks {
    public static final int MAX_AT_ONCE = 4;

    private ParallelPacks() {}

    public static boolean enabled() {
        String v = System.getProperty("lads.parallelpacks");
        return v == null || !BuiltInRender.switchedOff(v);
    }

    /** How many entries {@link #run} would run at once. */
    public static <T> int concurrency(List<T> items, Function<T, String> groupKey) {
        return Math.min(MAX_AT_ONCE, groups(items, groupKey).size());
    }

    private static <T> List<List<T>> groups(List<T> items, Function<T, String> groupKey) {
        Map<Object, List<T>> groups = new LinkedHashMap<>();
        for (T item : items) {
            String key = groupKey.apply(item);
            groups.computeIfAbsent(key != null ? key : new Object(), k -> new ArrayList<>()).add(item);
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * Runs task for every item and returns when all are done. Items with the same non-null group key (same URL and hash) run one
     * after another on one worker, in input order, so they never write the same file at once and the later ones can reuse the first's
     * result; different groups run concurrently, at most {@code maxAtOnce}. Tasks should catch their own failures; a Throwable that
     * escapes one is rethrown here after every other task finished.
     */
    public static <T> void run(List<T> items, Function<T, String> groupKey, int maxAtOnce, Consumer<T> task) {
        List<List<T>> groups = groups(items, groupKey);
        int threads = Math.min(maxAtOnce, groups.size());
        Throwable[] failure = new Throwable[1];
        if (threads <= 1) {
            for (T item : items) task.accept(item);
            return;
        }
        AtomicInteger n = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Lads-PackDownload-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (List<T> group : groups) futures.add(pool.submit(() -> {
                for (T item : group) {
                    try { task.accept(item); }
                    catch (Throwable t) { synchronized (failure) { if (failure[0] == null) failure[0] = t; } }
                }
            }));
            for (Future<?> f : futures) f.get();
        } catch (InterruptedException e) {
            pool.shutdownNow(); // the workers' downloads see the interrupt exactly like a vanilla one would
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            synchronized (failure) { if (failure[0] == null) failure[0] = e.getCause(); }
        } finally {
            pool.shutdown();
        }
        synchronized (failure) {
            if (failure[0] instanceof Error) throw (Error) failure[0];
            if (failure[0] instanceof RuntimeException) throw (RuntimeException) failure[0];
            if (failure[0] != null) throw new RuntimeException(failure[0]);
        }
    }

    /** The four vanilla download callbacks; size -1 means unknown. */
    public interface Progress {
        void requestStart();
        void downloadStart(long size);
        void downloadedBytes(long bytes);
        void requestFinished(boolean success);
    }

    /**
     * Merges the callbacks of {@code total} concurrent packs into one stream for the vanilla notifier, which counts packs one after
     * another: starts are forwarded as they happen, progress is the summed bytes over the summed sizes, and the finish callbacks are
     * held until the last pack is done (failures first), so the "downloaded"/"failed" end state is reported once and correctly.
     * Every call into the target is serialized on this object.
     */
    public static final class Aggregate {
        private final Progress target;
        private final int total;
        private int done;
        private long sizeSum, bytesSum;
        private boolean unknownSize;
        private final List<Boolean> finishes = new ArrayList<>();

        public Aggregate(Progress target, int total) {
            this.target = target;
            this.total = total;
        }

        /** One pack's view; call {@link PackProgress#done} exactly once when its download ended (also on failure). */
        public PackProgress forPack() { return new PackProgress(); }

        public final class PackProgress implements Progress {
            private long bytes;

            @Override public void requestStart() {
                synchronized (Aggregate.this) { target.requestStart(); }
            }

            @Override public void downloadStart(long size) {
                synchronized (Aggregate.this) {
                    if (size < 0) unknownSize = true; else sizeSum += size;
                    target.downloadStart(unknownSize ? -1 : sizeSum);
                    target.downloadedBytes(bytesSum);
                }
            }

            @Override public void downloadedBytes(long now) {
                synchronized (Aggregate.this) {
                    bytesSum += now - bytes;
                    bytes = now;
                    target.downloadedBytes(bytesSum);
                }
            }

            @Override public void requestFinished(boolean success) {
                synchronized (Aggregate.this) { finishes.add(success); }
            }

            public void done() {
                synchronized (Aggregate.this) {
                    if (++done < total) return;
                    for (boolean ok : finishes) if (!ok) target.requestFinished(false); // failures first, so the last call leaves the right toast
                    for (boolean ok : finishes) if (ok) target.requestFinished(true);
                }
            }
        }
    }
}
