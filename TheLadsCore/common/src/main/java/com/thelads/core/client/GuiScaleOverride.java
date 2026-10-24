package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * GUI Scale (Lunar's GUI Scale, Feather's fractional scale): a GUI scale between vanilla's whole steps, such as 2.5x, for the HUD
 * and every screen. 1.20.1 hands it to Window.setGuiScale as vanilla's resize does (that version keeps the scale as a double);
 * 1.8.9 lays out ScaledResolution with it. 26.x keeps the GUI scale as a whole number through its whole GUI renderer (projection,
 * scissor, text and item atlases), so the module is unavailable there. Visual only.
 */
public final class GuiScaleOverride {
    public static final String NAME = "GUI Scale", SCALE = "Scale";
    /** Vanilla's smallest GUI: a scale must leave at least this many GUI pixels across and down. */
    static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;
    public static boolean on;
    static double requested = 2;
    /** Changes whenever the module or Scale changes: the version lays the GUI out again. */
    public static int revision;
    private static Module module;

    private GuiScaleOverride() {}

    /** Once a client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        boolean enabled = module != null && module.isEnabled();
        double scale = enabled && module.getOption(SCALE) instanceof SliderOption slider ? slider.getValue() : requested;
        if (enabled != on || scale != requested) revision++;
        on = enabled;
        requested = scale;
    }

    /** The GUI scale for a framebuffer of {@code width} x {@code height} pixels: Scale, or the largest that still fits, never below 1. */
    public static double scale(int width, int height) {
        return Math.max(1, Math.min(requested, Math.min(width / (double) MIN_WIDTH, height / (double) MIN_HEIGHT)));
    }
}
