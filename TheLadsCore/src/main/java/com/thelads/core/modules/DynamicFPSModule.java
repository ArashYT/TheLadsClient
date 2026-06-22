package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
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

        int target = originalFramerateLimit;
        if (!isFocused) {
            target = fpsFor("Unfocused FPS", 15);
        } else if (isAfk) {
            target = fpsFor("AFK FPS", 20);
        } else {
            return originalFramerateLimit;
        }

        if (originalFramerateLimit != 0 && originalFramerateLimit != 260 && originalFramerateLimit < target) {
            return originalFramerateLimit;
        }
        return target;
    }

    /** Reads an FPS cycle option by name. Returns the encoded FPS value. */
    private int fpsFor(String optName, int def) {
        Option o = getOption(optName);
        if (o instanceof DropdownOption) {
            int idx = ((DropdownOption) o).getIndex();
            // Options: 1, 5, 10, 15, 20, 30, 60, Unlimited(0)
            int[] fps = { 1, 5, 10, 15, 20, 30, 60, 0 };
            if (idx >= 0 && idx < fps.length) return fps[idx];
        }
        return def;
    }

    public boolean isAfk() { return isAfk; }
}
