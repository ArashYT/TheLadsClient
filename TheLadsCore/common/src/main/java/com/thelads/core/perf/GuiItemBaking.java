package com.thelads.core.perf;

import com.thelads.core.modules.PerformanceManagerModule;

/**
 * GUI item baking (Polydium parity; 1.20.1 and 26.3).
 * Static GUI item models (items without animated textures or active glint) are cached and only redrawn
 * or retessellated when animated texture ticks advance or models are invalidated.
 */
public final class GuiItemBaking {
    private static volatile boolean disabled = "false".equalsIgnoreCase(System.getProperty("thelads.guiItemBaking"));
    private static long animationEpoch;

    private GuiItemBaking() {}

    public static boolean isEnabled() {
        return !disabled && PerformanceManagerModule.bool("GUI Item Baking", true);
    }

    public static void setDisabled(boolean off) {
        disabled = off;
    }

    /** Called on texture animation frame ticks to advance the animation epoch. */
    public static void tickAnimation() {
        animationEpoch++;
    }

    public static long getAnimationEpoch() {
        return animationEpoch;
    }

    /**
     * Determines whether an item with {@code frameCount} texture animation frames is static.
     * Items with 1 or fewer frames have no animation loop and can be baked statically.
     */
    public static boolean isStatic(int frameCount, boolean hasGlint) {
        if (!isEnabled()) return false;
        return frameCount <= 1 && !hasGlint;
    }
}
