package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.Locale;

/**
 * BorderlessFullscreen: fullscreen becomes an undecorated, ordinary desktop window over the current monitor (never a
 * monitor-owned, exclusive window), like the Borderless Gaming app.
 */
public final class BorderlessWindow {
    /**
     * The window is one row taller than the monitor. On Windows a window that exactly covers a monitor is promoted to
     * fullscreen/independent flip, which OBS window capture and Discord screen share cannot see. Mojang pads its own
     * borderless modes the same way (26.2 soft fullscreen, 26.3 one extra column).
     */
    public static final int EXTRA_HEIGHT = 1;

    private BorderlessWindow() {}

    /** macOS keeps its native fullscreen spaces. */
    public static boolean enabled() {
        Module module = ModuleManager.getInstance().getModule("BorderlessFullscreen");
        return module != null && module.isEnabled() && !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }
}
