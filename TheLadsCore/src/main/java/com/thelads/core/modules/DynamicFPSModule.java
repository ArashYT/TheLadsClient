package com.thelads.core.modules;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;
import com.thelads.core.config.BoolOption;
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
        addOption(new DropdownOption("Unfocused FPS", 3, "1", "5", "10", "15", "20", "30", "60", "Unlimited"));
        addOption(new DropdownOption("AFK FPS",       3, "1", "5", "10", "15", "20", "30", "60", "Unlimited"));
        addOption(new DropdownOption("Target FPS", 6, "30", "60", "120", "144", "165", "240", "Unlimited"));
        addOption(new BoolOption("Enable VSync", false));
        addOption(new BoolOption("Lower Render Distance", false));
        addOption(new DropdownOption("Lower Render Distance By", 0, "2 Chunks", "4 Chunks", "8 Chunks"));
        addOption(new SliderOption("Lower Master Volume By (%)", 0.0, 0.0, 100.0, 1.0));
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
            Option o = getOption("Target FPS");
            if (o instanceof DropdownOption) {
                int idx = ((DropdownOption) o).getIndex();
                int[] fps = { 30, 60, 120, 144, 165, 240, 0 };
                if (idx >= 0 && idx < fps.length) {
                    int val = fps[idx];
                    target = (val == 0) ? 260 : val;
                }
            }
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

    private boolean wasApplied = false;
    private Integer originalRenderDistance = null;
    private Boolean originalVsync = null;

    public void tick(Minecraft mc) {
        if (!isEnabled()) {
            if (wasApplied) {
                restoreSettings(mc);
            }
            return;
        }

        isFocused = mc.isWindowActive();
        isAfk = isFocused && (System.currentTimeMillis() - lastActivityMs) >= AFK_THRESHOLD_MS;

        boolean shouldLower = !isFocused || isAfk;

        if (shouldLower) {
            if (!wasApplied) {
                wasApplied = true;
                originalRenderDistance = mc.options.renderDistance().get();
                originalVsync = mc.options.enableVsync().get();

                Option vsyncOpt = getOption("Enable VSync");
                if (vsyncOpt instanceof BoolOption && ((BoolOption) vsyncOpt).get()) {
                    mc.options.enableVsync().set(true);
                }

                Option lowerDistOpt = getOption("Lower Render Distance");
                if (lowerDistOpt instanceof BoolOption && ((BoolOption) lowerDistOpt).get()) {
                    int reduceBy = 2;
                    Option byOpt = getOption("Lower Render Distance By");
                    if (byOpt instanceof DropdownOption) {
                        reduceBy = switch (((DropdownOption) byOpt).getIndex()) {
                            case 1 -> 4;
                            case 2 -> 8;
                            default -> 2;
                        };
                    }
                    mc.options.renderDistance().set(Math.max(2, originalRenderDistance - reduceBy));
                }
            }
        } else {
            if (wasApplied) {
                restoreSettings(mc);
            }
        }
    }

    private void restoreSettings(Minecraft mc) {
        if (originalRenderDistance != null) {
            mc.options.renderDistance().set(originalRenderDistance);
        }
        if (originalVsync != null) {
            mc.options.enableVsync().set(originalVsync);
        }
        wasApplied = false;
        originalRenderDistance = null;
        originalVsync = null;
    }
}
