package com.thelads.core.perf;

import com.thelads.core.modules.PerformanceManagerModule;

/**
 * Sodium vertex pipeline routing hook (Polydium parity; 1.20.1 and 26.3).
 * Routes entity, item, and text geometry through Sodium's optimized SIMD vertex transformation pipeline
 * when Sodium or Embeddium is loaded.
 */
public final class SodiumPipelineHook {
    private static volatile boolean disabled = "false".equalsIgnoreCase(System.getProperty("thelads.sodiumPipelineHook"));
    private static boolean sodiumLoaded;

    private SodiumPipelineHook() {}

    public static boolean isEnabled() {
        return !disabled && PerformanceManagerModule.bool("Sodium Vertex Pipeline Hook", true);
    }

    public static void setDisabled(boolean off) {
        disabled = off;
    }

    public static void setSodiumLoaded(boolean loaded) {
        sodiumLoaded = loaded;
    }

    public static boolean isSodiumLoaded() {
        return sodiumLoaded;
    }

    public static boolean shouldUseSodiumPipeline() {
        return isEnabled() && sodiumLoaded;
    }
}
