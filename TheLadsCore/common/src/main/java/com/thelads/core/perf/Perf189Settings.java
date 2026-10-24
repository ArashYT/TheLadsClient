package com.thelads.core.perf;

/**
 * Fast static flags for 1.8.9 rendering and lighting performance features (zero-allocation hot paths).
 */
public final class Perf189Settings {
    public static volatile boolean gpuEntityInstancing = false;
    public static volatile boolean starlightLighting = false;
    public static volatile boolean cameraParallaxFix = false;
    public static volatile boolean chunkCullingFix = false;

    private Perf189Settings() {}

    public static boolean isGpuEntityInstancing() {
        return gpuEntityInstancing;
    }

    public static boolean isStarlightLighting() {
        return starlightLighting;
    }

    public static boolean isCameraParallaxFix() {
        return cameraParallaxFix;
    }

    public static boolean isChunkCullingFix() {
        return chunkCullingFix;
    }
}
