package com.thelads.core.perf;

import com.thelads.core.modules.PerformanceManagerModule;

/**
 * Lighting, shadow and cull-box caches (Polydium parity; 1.20.1 and 26.3).
 * Caches block entity lighting lookups, entity cull bounding boxes, and ground shadow raycasts
 * across entity rendering frames. Direct-mapped cache tables ensure zero object allocation on hot paths.
 */
public final class LightingShadowCache {
    private static final int TABLE_SIZE = 1024;
    private static final int MASK = TABLE_SIZE - 1;
    private static final long[] LIGHT_KEYS = new long[TABLE_SIZE];
    private static final int[] LIGHT_VALUES = new int[TABLE_SIZE];
    private static final long[] SHADOW_KEYS = new long[TABLE_SIZE];
    private static final float[] SHADOW_VALUES = new float[TABLE_SIZE];

    private static volatile boolean disabled = "false".equalsIgnoreCase(System.getProperty("thelads.lightingShadowCache"));

    static {
        clear();
    }

    private LightingShadowCache() {}

    public static boolean isEnabled() {
        return !disabled && PerformanceManagerModule.bool("Lighting & Shadow Caching", true);
    }

    public static void setDisabled(boolean off) {
        disabled = off;
    }

    public static int getLight(long posKey) {
        if (!isEnabled()) return -1;
        int idx = (int) (posKey ^ (posKey >>> 16)) & MASK;
        return LIGHT_KEYS[idx] == posKey ? LIGHT_VALUES[idx] : -1;
    }

    public static void putLight(long posKey, int light) {
        if (!isEnabled()) return;
        int idx = (int) (posKey ^ (posKey >>> 16)) & MASK;
        LIGHT_KEYS[idx] = posKey;
        LIGHT_VALUES[idx] = light;
    }

    public static float getShadow(long entityPosKey) {
        if (!isEnabled()) return Float.NaN;
        int idx = (int) (entityPosKey ^ (entityPosKey >>> 16)) & MASK;
        return SHADOW_KEYS[idx] == entityPosKey ? SHADOW_VALUES[idx] : Float.NaN;
    }

    public static void putShadow(long entityPosKey, float groundY) {
        if (!isEnabled()) return;
        int idx = (int) (entityPosKey ^ (entityPosKey >>> 16)) & MASK;
        SHADOW_KEYS[idx] = entityPosKey;
        SHADOW_VALUES[idx] = groundY;
    }

    public static void clear() {
        for (int i = 0; i < TABLE_SIZE; i++) {
            LIGHT_KEYS[i] = Long.MIN_VALUE;
            LIGHT_VALUES[i] = -1;
            SHADOW_KEYS[i] = Long.MIN_VALUE;
            SHADOW_VALUES[i] = Float.NaN;
        }
    }
}
