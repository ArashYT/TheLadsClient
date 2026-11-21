package com.thelads.core.perf;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared bits of the built-in fast resource pack screen (the optimization part of FastPacks): pack icons are decoded on two background
 * threads and uploaded to the GPU on the render thread, with the default icon shown until then. Always on;
 * {@code -Dlads.fastpackscreen=false} turns it off. The per-version mixins do the hooking.
 */
public final class FastPackScreen {
    private static ExecutorService pool;

    private FastPackScreen() {}

    public static boolean enabled() {
        String v = System.getProperty("lads.fastpackscreen");
        return v == null || !BuiltInRender.switchedOff(v);
    }

    /** Two low-priority daemon threads for pack.png decoding. */
    public static synchronized ExecutorService decoder() {
        if (pool == null) {
            AtomicInteger n = new AtomicInteger();
            pool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "Lads-PackIcon-" + n.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }
        return pool;
    }

    public static String openLine(int icons) {
        return "[Lads] fast pack screen: " + icons + " icons queued";
    }
}
