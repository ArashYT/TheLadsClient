package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import net.minecraft.client.Minecraft;

public class DynamicFPSModule extends Module {

    private boolean isFocused = true;
    private boolean isAfk     = false;
    private int     originalFramerateLimit = 60;

    // Tracks last movement/click tick for AFK detection
    private long lastActivityMs = System.currentTimeMillis();
    private static final long AFK_THRESHOLD_MS = 30_000L; // 30 seconds of inactivity

    public DynamicFPSModule() {
        super("DynamicFPS", "Reduces framerate when the game window is unfocused or idle.");
    }

    public void onWindowFocusChanged(boolean focused) {
        this.isFocused = focused;
        if (focused) onInput(); // regain focus = not AFK
    }

    /** Call this whenever the player inputs something (mouse, keyboard). */
    public void onInput() {
        lastActivityMs = System.currentTimeMillis();
    }

    public void setOriginalFramerateLimit(int limit) {
        this.originalFramerateLimit = limit;
    }

    public int getCurrentFramerateLimit() {
        if (!isEnabled()) return originalFramerateLimit;

        // AFK check (only when the game window is focused)
        isAfk = isFocused && (System.currentTimeMillis() - lastActivityMs) >= AFK_THRESHOLD_MS;

        if (!isFocused) return fpsFor("Unfocused FPS", 10);
        if (isAfk)      return fpsFor("AFK FPS",       20);
        return originalFramerateLimit; // normal focused play
    }

    /** Reads an FPS cycle option by name. Returns the encoded FPS value. */
    private int fpsFor(String optName, int def) {
        Option o = getOption(optName);
        if (o instanceof CycleOption) {
            int idx = ((CycleOption) o).getIndex();
            // Options: 1, 5, 10, 15, 20, 30, 60, Unlimited(0)
            int[] fps = { 1, 5, 10, 15, 20, 30, 60, 0 };
            if (idx >= 0 && idx < fps.length) return fps[idx];
        }
        return def;
    }

    public boolean isAfk() { return isAfk; }
}
