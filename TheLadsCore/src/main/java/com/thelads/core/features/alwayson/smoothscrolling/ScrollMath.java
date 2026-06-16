package com.thelads.core.features.alwayson.smoothscrolling;

public class ScrollMath {
    public static final double scrollSpeed = 0.5;
    public static final double scrollbarDrag = 0.025;
    public static final double animationDuration = 1.0;
    public static final double pushBackStrength = 1.0;

    public static double scrollbarVelocity(double timer, double factor) {
        return Math.pow(1.0 - scrollbarDrag, timer) * factor;
    }

    public static int dampenSquish(double squish, int height) {
        double proportion = Math.min(1.0, squish / 100.0);
        return (int)(Math.min(0.85, proportion) * (double)height);
    }

    public static double pushBackStrength(double distance, float delta) {
        return (distance + 4.0) * (double)delta / 0.3 / (3.2 / pushBackStrength);
    }
}
