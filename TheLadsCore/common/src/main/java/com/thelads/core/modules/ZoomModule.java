package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

/**
 * Lads Zoom on every version: the adapters report the zoom key, scrolls and when gameplay stops (a screen, focus loss, death,
 * another world), and multiply the FOV Minecraft computed (dynamic/sprint FOV included) by {@link #fovFactor}.
 * Smooth Zoom eases by real time, not ticks, so it looks the same at any frame rate and never overshoots; it moves in log
 * space, so 1x to 4x feels as even as 4x back to 1x. The vanilla FOV option is never written, so unzoomed FOV is exact.
 */
public class ZoomModule extends Module {
    public static final float DEFAULT_ZOOM = 0.25f, MIN_ZOOM = 0.05f, MAX_ZOOM = 0.8f;
    /** One scroll notch zooms in or out by this ratio. */
    static final double SCROLL_STEP = 1.25;
    /** Smooth Zoom speed per second: 95% of the way in 0.25 s. */
    static final double RATE = 12;
    private static final int TOGGLE = 1;
    private final DropdownOption mode;
    private final BoolOption smoothZoom, scrollZoom, handZoom;
    private boolean active;
    private float target = DEFAULT_ZOOM;
    private double factor = 1;
    private long lastNanos;

    public ZoomModule() {
        super("Zoom", "Hold or toggle the zoom key to narrow your FOV; scroll while zoomed to change how far.");
        mode = addOption(new DropdownOption("Mode", 0, "Hold", "Toggle"));
        smoothZoom = addOption(new BoolOption("Smooth Zoom", true));
        scrollZoom = addOption(new BoolOption("Scroll to Zoom", true));
        handZoom = addOption(new BoolOption("Hand Zoom", true));
    }

    /** The zoom key went down or up during gameplay. */
    public void key(boolean down) {
        if (!isEnabled()) return;
        boolean next = mode.getIndex() == TOGGLE ? active != down : down;
        if (next && !active) target = DEFAULT_ZOOM; // each zoom starts at the default; the scrolled level lasts until it ends
        active = next;
    }

    /** Gameplay stopped (a screen opened, focus or the key was lost): zooms out, smoothly when Smooth Zoom is on. */
    public void release() {
        active = false;
    }

    /** Another world or player: back to 1x at once. */
    public void reset() {
        active = false;
        target = DEFAULT_ZOOM;
        factor = 1;
        lastNanos = 0;
    }

    @Override
    public void onDisable() {
        reset();
    }

    public boolean isActive() {
        return active && isEnabled();
    }

    /** Scroll while zoomed: true when the scroll was used (the hotbar must not move). Notches up (positive) zoom in. */
    public boolean scroll(double notches) {
        if (!isActive() || !scrollZoom.get() || notches == 0 || Double.isNaN(notches)) return false;
        target = (float) Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, target * Math.pow(SCROLL_STEP, -notches)));
        return true;
    }

    /** The FOV multiplier for a frame rendered at {@code nanos} (System.nanoTime()); 1 is unzoomed. Hand: the held-item FOV. */
    public float fovFactor(boolean hand, long nanos) {
        if (!isEnabled()) return 1;
        double goal = active ? target : 1;
        double seconds = lastNanos == 0 ? 0 : Math.max(0, (nanos - lastNanos) / 1e9);
        lastNanos = nanos;
        factor = smoothZoom.get() ? approach(factor, goal, seconds) : goal;
        return hand && !handZoom.get() ? 1 : (float) factor;
    }

    /** Mouse turn multiplier: the camera turns as much per pixel on screen zoomed as unzoomed. */
    public float sensitivity() {
        return isEnabled() ? (float) factor : 1;
    }

    /** Exponential ease of {@code from} toward {@code to} over {@code seconds}, in log space; lands exactly on {@code to}. */
    static double approach(double from, double to, double seconds) {
        double next = to * Math.pow(from / to, Math.exp(-RATE * seconds));
        return Math.abs(Math.log(next / to)) < 1e-4 ? to : next;
    }
}
