package com.thelads.core.client.gui;

/**
 * Shared smooth scroll constants and helpers.
 * When smooth scrolling is active, wheel steps cover 1.5x the distance of vanilla/instant scrolling.
 */
public final class SmoothScroll {
    /** 1.9.14: 1.5x more lines when smooth scrolling in any menu. */
    public static final double FACTOR = 1.5;

    /**
     * Advances eased scroll position toward target over elapsed time {@code dt} seconds.
     * Snaps to target when within 0.2 pixels.
     */
    public static double step(double current, double target, double dt) {
        double next = current + (target - current) * (1.0 - Math.exp(-dt * 18.0));
        return Math.abs(next - target) < 0.2 ? target : next;
    }

    private SmoothScroll() {}
}
