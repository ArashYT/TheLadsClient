package com.thelads.core.perf;

/**
 * Fast static flags for 1.8.9 rendering and lighting performance features (zero-allocation hot paths).
 */
public final class Perf189Settings {
    public static volatile boolean cameraParallaxFix = false;

    private Perf189Settings() {}

    public static boolean isCameraParallaxFix() {
        return cameraParallaxFix;
    }
}
