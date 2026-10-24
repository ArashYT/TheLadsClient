package com.thelads.core.perf;

import com.thelads.core.modules.PerformanceManagerModule;

/**
 * Deferred sky rendering (Polydium parity; 1.20.1 and 26.3).
 * Defers sky disk / sun / moon / star rendering until after terrain passes or ensures early-Z depth testing
 * is enabled with LEQUAL, skipping occluded sky pixels behind opaque terrain.
 */
public final class DeferredSky {
    private static volatile boolean disabled = "false".equalsIgnoreCase(System.getProperty("thelads.deferredSky"));
    private static boolean active;

    private DeferredSky() {}

    public static boolean isEnabled() {
        return !disabled && PerformanceManagerModule.bool("Deferred Sky Rendering", true);
    }

    public static void setDisabled(boolean off) {
        disabled = off;
    }

    public static void setActive(boolean state) {
        active = state;
    }

    public static boolean isActive() {
        return active;
    }
}
