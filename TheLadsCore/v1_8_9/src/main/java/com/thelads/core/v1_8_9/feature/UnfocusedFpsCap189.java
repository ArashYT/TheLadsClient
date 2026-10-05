package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import org.lwjgl.opengl.Display;

/**
 * Unfocused FPS cap (1.7.3): while the game window is not focused, the frame rate limit is at most 60 (the module's slider). It is
 * applied through Minecraft.getLimitFramerate (MinecraftMixin), so it only ever lowers the player's own limit, never raises it. The
 * title screen needs no cap of its own: vanilla already limits a menu without a world to 30.
 */
public final class UnfocusedFpsCap189 {
    public static final String NAME = "UnfocusedFpsCap";
    private static final String LIMIT = "FPS limit";
    /** The QA harness sets this so its (never focused) game windows keep the FPS the probes and benchmarks ask for; never in the benchmark. */
    static boolean bypass = Boolean.getBoolean("thelads.noUnfocusedCap") || "perf".equals(System.getProperty("thelads.verify189Only"));
    /** QA only (Probe173Cap): count the window as unfocused, which a QA window the owner does not click cannot be made to be. */
    static boolean forceUnfocused;

    private UnfocusedFpsCap189() {}

    /** Before the config loads, so the saved on/off and limit apply; new installs start on. */
    public static void register() {
        Module module = new Module(NAME, "Caps the frame rate while the game window is not in front, to save power and keep other programs smooth. "
            + "Never raises your own FPS limit.");
        module.addOption(new SliderOption(LIMIT, 60, 10, 240, 5));
        module.setEnabled(true);
        ModuleManager.getInstance().register(module, Module.Category.MECHANIC);
    }

    /** Minecraft's frame rate limit for this frame, given the one it would use. */
    public static int limit(int vanilla) {
        if (bypass || (Display.isActive() && !forceUnfocused)) return vanilla;
        Module module = ModuleManager.getInstance().getModule(NAME);
        if (module == null || !module.isEnabled()) return vanilla;
        return Math.min(vanilla, (int) Options189.number(NAME, LIMIT, 60));
    }
}
