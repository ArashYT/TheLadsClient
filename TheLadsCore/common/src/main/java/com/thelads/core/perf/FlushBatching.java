package com.thelads.core.perf;

/**
 * Packet Flush Batching: the client channel gets a flush-consolidation handler, so several writes in one event-loop pass reach the
 * socket in one flush. Order and content of packets stay the same; there is no timer and no per-tick batching.
 * Kill switch: -Dlads.flushBatch=false. Mirrored from the "Packet Flush Batching" option in Performance; read at channel init.
 */
public final class FlushBatching {
    /** Flushes pending in one event-loop pass before the handler flushes anyway (Netty's FlushConsolidationHandler argument). */
    public static final int EXPLICIT_FLUSH_AFTER = 256;
    private static final boolean PROPERTY_ON = !"false".equalsIgnoreCase(System.getProperty("lads.flushBatch"));
    public static volatile boolean enabled = true;

    private FlushBatching() {}

    /** True when a new client connection should get the handler (krypton does the same job itself). */
    public static boolean shouldInstall(boolean kryptonLoaded) {
        return PROPERTY_ON && enabled && !kryptonLoaded;
    }
}
