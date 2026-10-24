package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DarkModeModule;

public final class DarkMode {
    public static final String MODULE_NAME = "Dark Mode";
    private static DarkModeModule module;
    private static volatile boolean active;
    private static volatile boolean darkenContainers = true;
    private static volatile float dimFraction = 0.6f;

    private DarkMode() {}

    public static void setModule(DarkModeModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        DarkModeModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof DarkModeModule dm) {
                m = dm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        darkenContainers = m.darkenContainers.get();
        dimFraction = (float) (m.backgroundDim.getValue() / 100.0);
    }

    public static boolean isEnabled() { return active; }
    public static boolean shouldDarkenContainers() { return active && darkenContainers; }
    public static float getDimFraction() { return dimFraction; }

    public static int getContainerTint() {
        int v = (int) ((1.0f - dimFraction * 0.7f) * 255.0f);
        if (v < 40) v = 40;
        if (v > 255) v = 255;
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }
}
