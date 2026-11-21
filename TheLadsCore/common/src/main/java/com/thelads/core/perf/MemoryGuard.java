package com.thelads.core.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Low-overhead memory management. Called from each version's client tick (no thread, no timer): every 30 s it reads the heap, and
 * above 85% of max runs the trim callbacks of our own lazily-rebuilding caches (at most once per 2 minutes). Never calls
 * System.gc() from that check. On world leave or dimension change {@link #levelChanged()} clears level-bound caches, and only
 * when "Aggressive Memory Cleaner" is on it asks for one System.gc() at that moment.
 * Kill switch: -Dlads.memoryGuard=false. Options: "Memory Guard" and "Aggressive Memory Cleaner" in Performance.
 */
public final class MemoryGuard {
    public static final long CHECK_INTERVAL_MS = 30_000L;
    public static final long TRIM_COOLDOWN_MS = 120_000L;
    public static final double THRESHOLD = 0.85;

    /** Debug: -Dlads.memoryguard.threshold=0.01 makes the next check trim at once (heap fraction, 0.01-0.99). */
    private static final double TRIM_AT = threshold();
    private static double threshold() {
        try { double d = Double.parseDouble(System.getProperty("lads.memoryguard.threshold", "")); if (d >= 0.01 && d <= 0.99) return d; } catch (NumberFormatException ignored) { }
        return THRESHOLD;
    }
    private static final boolean PROPERTY_ON = !"false".equalsIgnoreCase(System.getProperty("lads.memoryGuard"));
    public static volatile boolean enabled = true;
    public static volatile boolean aggressiveCleaner = false;

    /** Heap and clock sources, replaceable in tests. */
    public interface Heap { long used(); long max(); }
    public interface Clock { long millis(); }

    private static final class Entry {
        final String name; final Runnable trim;
        Entry(String name, Runnable trim) { this.name = name; this.trim = trim; }
    }

    private final List<Entry> trims = new CopyOnWriteArrayList<Entry>();
    private final List<Entry> level = new CopyOnWriteArrayList<Entry>();

    private static final MemoryGuard INSTANCE = new MemoryGuard(new Heap() {
        @Override public long used() { Runtime r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }
        @Override public long max() { return Runtime.getRuntime().maxMemory(); }
    }, new Clock() {
        @Override public long millis() { return System.currentTimeMillis(); }
    });

    private final Heap heap;
    private final Clock clock;
    private long lastCheck;
    private long lastTrim = Long.MIN_VALUE;
    private boolean started;

    public MemoryGuard(Heap heap, Clock clock) { this.heap = heap; this.clock = clock; }

    /** A cache we own that rebuilds itself on demand; trim must be safe on the client thread and free memory. */
    public static void register(String name, Runnable trim) { INSTANCE.add(name, trim); }
    public void add(String name, Runnable trim) { trims.add(new Entry(name, trim)); }

    /** A cache keyed by positions, entities or chunks of the current level; cleared when the player leaves or changes world. */
    public static void registerLevelBound(String name, Runnable clear) { INSTANCE.addLevelBound(name, clear); }
    public void addLevelBound(String name, Runnable clear) { level.add(new Entry(name, clear)); }

    private static boolean defaultsRegistered;

    /** Registers the shared caches (text widths, chat translations, pack name filter); each version calls this once at client init. */
    public static synchronized void registerDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;
        register("FontWidthCache", FontWidthCache::clear);
        register("ChatTranslator", com.thelads.core.client.ChatTranslator::trimCache);
        register("PackFilterHelper", com.thelads.core.client.pack.PackFilterHelper::trimCache);
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] MemoryGuard active: check every 30 s, trim above 85% heap, 2 min between trims");
    }

    private static java.lang.ref.WeakReference<Object> lastLevel = new java.lang.ref.WeakReference<Object>(null);

    /**
     * Call once per client tick with the current client level (null outside a world). A different level object than last tick
     * (world left, joined, or dimension change) clears the level-bound caches; the level is held weakly.
     */
    public static void tick(Object level) {
        if (!PROPERTY_ON || !enabled) return;
        if (level != lastLevel.get()) {
            lastLevel = new java.lang.ref.WeakReference<Object>(level);
            levelChanged();
        }
        INSTANCE.check();
    }

    /** Call when the world is left or the dimension changes. */
    public static void levelChanged() {
        if (PROPERTY_ON && enabled) INSTANCE.clearLevelBound(aggressiveCleaner);
    }

    /** Runs the level-bound clears; {@code gc} true requests the single System.gc() for this world change. */
    public int clearLevelBound(boolean gc) {
        int cleared = 0;
        for (Entry e : level) {
            try { e.trim.run(); cleared++; } catch (Throwable t) { /* a cache must never break a world change */ }
        }
        if (gc) System.gc();
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] MemoryGuard cleared {} level caches (world or dimension change)", cleared);
        return cleared;
    }

    /** Wall-clock gated: cheap when called every tick. Returns the number of caches trimmed (0 when nothing ran). */
    public int check() {
        long now = clock.millis();
        if (!started) { started = true; lastCheck = now; return 0; }
        if (now - lastCheck < CHECK_INTERVAL_MS) return 0;
        lastCheck = now;
        long max = heap.max();
        long used = heap.used();
        if (max <= 0 || used <= (long) (max * TRIM_AT)) return 0;
        if (lastTrim != Long.MIN_VALUE && now - lastTrim < TRIM_COOLDOWN_MS) return 0;
        lastTrim = now;
        int trimmed = 0;
        for (Entry e : new ArrayList<Entry>(trims)) {
            try { e.trim.run(); trimmed++; } catch (Throwable t) { /* skip a failing cache, trim the others */ }
        }
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] MemoryGuard trimmed {} caches, heap {} / {} MB",
            trimmed, used >> 20, max >> 20);
        return trimmed;
    }
}
