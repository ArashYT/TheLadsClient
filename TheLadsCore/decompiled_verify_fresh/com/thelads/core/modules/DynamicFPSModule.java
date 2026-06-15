/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.modules;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.Option;

public class DynamicFPSModule
extends Module {
    private boolean isFocused = true;
    private boolean isAfk = false;
    private int originalFramerateLimit = 60;
    private long lastActivityMs = System.currentTimeMillis();
    private static final long AFK_THRESHOLD_MS = 30000L;

    public DynamicFPSModule() {
        super("DynamicFPS", "Reduces framerate when the game window is unfocused or idle.");
    }

    public void onWindowFocusChanged(boolean focused) {
        this.isFocused = focused;
        if (focused) {
            this.onInput();
        }
    }

    public void onInput() {
        this.lastActivityMs = System.currentTimeMillis();
    }

    public void setOriginalFramerateLimit(int limit) {
        this.originalFramerateLimit = limit;
    }

    public int getCurrentFramerateLimit() {
        if (!this.isEnabled()) {
            return this.originalFramerateLimit;
        }
        boolean bl = this.isAfk = this.isFocused && System.currentTimeMillis() - this.lastActivityMs >= 30000L;
        if (!this.isFocused) {
            return this.fpsFor("Unfocused FPS", 10);
        }
        if (this.isAfk) {
            return this.fpsFor("AFK FPS", 20);
        }
        return this.originalFramerateLimit;
    }

    private int fpsFor(String optName, int def) {
        Option o = this.getOption(optName);
        if (o instanceof CycleOption) {
            int idx = ((CycleOption)o).getIndex();
            int[] fps = new int[]{1, 5, 10, 15, 20, 30, 60, 0};
            if (idx >= 0 && idx < fps.length) {
                return fps[idx];
            }
        }
        return def;
    }

    public boolean isAfk() {
        return this.isAfk;
    }
}

