package com.thelads.core.client.gui;

/**
 * Shared smooth scroll constants and helpers.
 * When smooth scrolling is active, wheel steps cover 1.5x the distance of vanilla/instant scrolling.
 */
public final class SmoothScroll {
    /** 1.9.14: 1.5x more lines when smooth scrolling in any menu. */
    public static final double FACTOR = 1.5;

    private SmoothScroll() {}
}
