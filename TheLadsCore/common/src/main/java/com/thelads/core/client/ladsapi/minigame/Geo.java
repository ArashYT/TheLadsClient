package com.thelads.core.client.ladsapi.minigame;

/** Angles as the game has them: yaw 0 looks toward +Z, 90 toward -X; pitch -90 up, +90 down. */
public final class Geo {
    private Geo() {}

    /** From {@code a} to {@code b} along the shorter arc. */
    public static double lerpAngle(double a, double b, double f) {
        double d = ((b - a) % 360 + 540) % 360 - 180;
        return a + d * f;
    }

    /** The yaw that looks from the first point to the second. */
    public static double yawTo(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static double pitchTo(double dx, double dy, double dz) {
        return -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }
}
