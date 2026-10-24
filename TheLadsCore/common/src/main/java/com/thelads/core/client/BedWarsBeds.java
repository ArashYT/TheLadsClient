package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.BedWarsBedsModule;

public final class BedWarsBeds {
    public static final String MODULE_NAME = "BedWars Beds";
    private static BedWarsBedsModule module;
    private static volatile boolean active;
    private static volatile int colorMode;
    private static volatile boolean destructionEffect = true;

    private static final int[] COLORS = {
        0xFFE50000, 0xFFE50000, 0xFF0055FF, 0xFF00AA00,
        0xFFFFDD00, 0xFF00FFFF, 0xFFFFFFFF, 0xFFFF88FF, 0xFF888888
    };

    private BedWarsBeds() {}

    public static void setModule(BedWarsBedsModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        BedWarsBedsModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof BedWarsBedsModule bm) {
                m = bm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        colorMode = m.colorMode.getIndex();
        destructionEffect = m.destructionEffect.get();
    }

    public static boolean isEnabled() { return active; }
    public static boolean hasDestructionEffect() { return active && destructionEffect; }

    public static int getBedColor() {
        if (colorMode >= 0 && colorMode < COLORS.length) return COLORS[colorMode];
        return COLORS[0];
    }

    public static float getRed() { return ((getBedColor() >> 16) & 0xFF) / 255.0f; }
    public static float getGreen() { return ((getBedColor() >> 8) & 0xFF) / 255.0f; }
    public static float getBlue() { return (getBedColor() & 0xFF) / 255.0f; }
}
