package com.thelads.core.v1_8_9.gui;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Smooth wheel scrolling for 1.8.9's lists (GuiSlotMixin), as SmoothScrollMixin on 1.21.x and AbstractScrollArea on 26.x. The
 * lists still gliding are advanced once per frame here, not in their drawScreen, which some GuiSlot subclasses replace.
 */
public final class SmoothScroll189 {
    public interface Target {
        /** One frame of the glide; false when it ended (reached, or something else moved the list). */
        boolean ladsAdvanceScroll(double seconds);
    }

    private static final Set<Target> GLIDING = Collections.newSetFromMap(new IdentityHashMap<Target, Boolean>());
    private static long lastFrame;

    private SmoothScroll189() {}

    public static boolean gliding(Target list) { return GLIDING.contains(list); }

    public static void start(Target list) { GLIDING.add(list); }

    /** Every frame (RenderTickEvent START, before the screen draws). */
    public static void frame() {
        long now = System.nanoTime();
        double seconds = Math.min(.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (!GLIDING.isEmpty()) GLIDING.removeIf(list -> !list.ladsAdvanceScroll(seconds));
    }

    /** The 1.21.x step: the remaining distance shrinks by e^(-18 dt) per frame, then snaps when under 0.2 px. */
    public static float step(float from, float to, double seconds) {
        float next = (float) (from + (to - from) * (1 - Math.exp(-seconds * 18)));
        return Math.abs(next - to) < .2f ? to : next;
    }
}
