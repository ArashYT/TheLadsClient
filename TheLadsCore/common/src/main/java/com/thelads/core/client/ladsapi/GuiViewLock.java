package com.thelads.core.client.ladsapi;

/**
 * Server GUI pages (LadsGUI) freeze the camera: while the server's "Lock View" rule is on, {@link #pose} gives the rotation the camera must show instead of
 * the player's own. When the rule ends the freeze holds until the player's rotation is back on the frozen one (the server restores it right after) or a
 * second has passed, so the view never snaps through the mouse-driven cursor rotation.
 */
public final class GuiViewLock {
    private static final long GRACE_MS = 1000;
    private static final double CLOSE_DEG = 1.0;
    private static boolean held;
    private static double heldYaw, heldPitch;
    private static long releaseAt;

    private GuiViewLock() {}

    /** The camera's {yaw, pitch} to show now, or null to leave the camera alone. {@code yaw}/{@code pitch} are the player's own rotation. */
    public static synchronized double[] pose(double yaw, double pitch, long nowMs) {
        if (ServerSettings.viewLocked()) {
            held = true;
            releaseAt = 0;
            heldYaw = ServerSettings.lockYaw();
            heldPitch = ServerSettings.lockPitch();
            return new double[] {heldYaw, heldPitch};
        }
        if (!held) return null;
        if (releaseAt == 0) releaseAt = nowMs + GRACE_MS;
        if (nowMs >= releaseAt || (Math.abs(wrap(yaw - heldYaw)) < CLOSE_DEG && Math.abs(pitch - heldPitch) < CLOSE_DEG)) {
            held = false;
            return null;
        }
        return new double[] {heldYaw, heldPitch};
    }

    /** Wraps degrees into [-180, 180), like the shader's cursor math. */
    public static double wrap(double deg) {
        return deg - 360.0 * Math.floor((deg + 180.0) / 360.0);
    }

    /** The cursor's tip on the 1920x1080 canvas, relative to the screen centre (x right, y down), from the player's own rotation. */
    public static double[] cursor(double yaw, double pitch, double gain) {
        return new double[] {
            Math.max(-960, Math.min(960, gain * wrap(yaw))),
            Math.max(-540, Math.min(540, gain * pitch))};
    }

    /** Framebuffer pixels per canvas pixel for a window (the 16:9 canvas is fitted inside it), as the shader maps it. */
    public static double scale(int width, int height) {
        double aspect = (double) width / height;
        return Math.min(1.0, aspect / (16.0 / 9.0)) * height / 1080.0;
    }

    static synchronized void reset() {
        held = false;
        releaseAt = 0;
    }
}
