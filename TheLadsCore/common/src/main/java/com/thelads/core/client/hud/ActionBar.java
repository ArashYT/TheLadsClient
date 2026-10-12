package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * The action bar (vanilla's overlay message: what a server sends there, and a record's "Now playing"), as each version hands it
 * over from its own setter and ticks it where vanilla counts its time down. With the Action Bar module on, the module draws it
 * (ActionBarHudElement) and vanilla never gets it; off, vanilla shows it unchanged and this only follows along, so kill banners
 * can keep clear of it wherever it shows ({@link #spot}).
 */
public final class ActionBar {
    public static final String MODULE = "Action Bar";
    /** Vanilla's time for a message (3 s) and its fade (the last second). */
    static final int TICKS = 60, FADE = 20;
    /** The message as the version has it (a chat component; 1.8.9: a string with its formatting codes). */
    private static Object text;
    private static int time;
    private static boolean animated;
    /** Where the module last laid it out, in GUI units: left, top, right, bottom. */
    private static int[] placed;

    private ActionBar() {}

    /** A new message; {@code animated}: a record's "Now playing" (its colour cycles). True when the module shows it, so vanilla must not. */
    public static boolean set(Object message, boolean animated) {
        boolean module = takesOver();
        text = message;
        ActionBar.animated = animated;
        time = module ? (int) Math.round(number("Duration", 3) * 20) : TICKS;
        return module;
    }

    /** Each client tick the game is not paused (where vanilla counts its message down). */
    public static void tick() {
        if (time > 0) time--;
    }

    public static boolean takesOver() {
        Module module = ModuleManager.getInstance().getModule(MODULE);
        return module != null && module.isEnabled();
    }

    /** The message showing now, or null. */
    public static Object text() {
        return time > 0 ? text : null;
    }

    public static boolean animated() { return animated; }

    /** Ticks left, {@code partial} of the current one already gone. */
    public static float left(float partial) {
        return time - partial;
    }

    /** Its opacity (0 to 255) with {@code left} ticks to go: vanilla's last-second fade, or none with Fade Out off. */
    public static int alpha(float left) {
        if (left <= 0) return 0;
        return !bool("Fade Out", true) ? 255 : Math.min(255, (int) (left * 255 / FADE));
    }

    /** Vanilla's colour for it: white, or a record's cycling hue. */
    public static int rgb(float left) {
        return animated ? java.awt.Color.HSBtoRGB(left / 50f, .7f, .6f) & 0xFFFFFF : 0xFFFFFF;
    }

    /** ActionBarHudElement: where the module laid it out this frame, in GUI units. */
    static void placed(int left, int top, int right, int bottom) {
        placed = new int[] {left, top, right, bottom};
    }

    /**
     * The action bar on screen now, in the GUI units of {@code g}: {left, top, right, bottom} of its text, or null when none shows.
     * Vanilla's sits centred 68 px above the bottom, above the hotbar's lift; the module's where it was laid out.
     */
    public static int[] spot(LadsGraphics g) {
        if (text() == null) return null;
        if (takesOver()) return bool("Hide Action Bar", false) ? null : placed;
        int width = g.actionBarWidth(text), left = g.getScaledWidth() / 2 - width / 2, top = g.getScaledHeight() - g.hotbarLift() - 72;
        return new int[] {left, top, left + width, top + 9};
    }

    /** QA and tests: no message. */
    public static void clear() {
        text = null;
        time = 0;
        placed = null;
    }

    private static boolean bool(String option, boolean fallback) {
        Module module = ModuleManager.getInstance().getModule(MODULE);
        return module != null && module.getOption(option) instanceof BoolOption b ? b.get() : fallback;
    }

    private static double number(String option, double fallback) {
        Module module = ModuleManager.getInstance().getModule(MODULE);
        return module != null && module.getOption(option) instanceof SliderOption s ? s.getValue() : fallback;
    }
}
