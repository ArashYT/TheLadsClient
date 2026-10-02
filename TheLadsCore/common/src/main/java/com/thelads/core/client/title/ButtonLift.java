package com.thelads.core.client.title;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hover state of the title and pause menu buttons: each eases towards 1 while hovered and back to 0, and lifting buttons
 * draw a little bigger with a glow (TitleScreenTheme.LIFT). Render thread only.
 */
public final class ButtonLift {
    private static final Map<Object, float[]> STATE = new WeakHashMap<>(); // {progress, last frame seconds}
    private static final Set<Object> LIFTING = Collections.newSetFromMap(new WeakHashMap<>());
    private ButtonLift() {}

    /** Advances the button's hover and returns it, linear 0..1; call once per drawn frame. */
    public static float update(Object button, boolean hovered) {
        float now = (float) (System.nanoTime() / 1e9 % 100_000);
        float[] s = STATE.computeIfAbsent(button, b -> new float[] {0, now});
        float dt = Math.max(0, Math.min(.1f, now - s[1]));
        s[1] = now;
        s[0] = Math.max(0, Math.min(1, s[0] + (hovered ? dt : -dt) * 8));
        return s[0];
    }

    /** A vanilla button the screen lifts as a whole, its own label included (the pause menu's). */
    public static void enable(Object button) { LIFTING.add(button); }

    public static boolean enabled(Object button) { return LIFTING.contains(button); }

    /** The eased hover last computed, 0 for a button that does not lift. */
    public static float eased(Object button) {
        float[] s = STATE.get(button);
        float p = s == null ? 0 : s[0];
        return p * p * (3 - 2 * p);
    }
}
