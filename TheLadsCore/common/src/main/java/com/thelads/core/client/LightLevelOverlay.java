package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.LightLevelOverlayModule;

public final class LightLevelOverlay {
    public static final String MODULE_NAME = "LightLevelOverlay";
    private static LightLevelOverlayModule module;
    private static volatile boolean active;
    private static volatile int mode;
    private static volatile int renderDistance = 16;
    private static volatile boolean showSafeBlocks;
    private static volatile int nightSpawnColor = 0xFFFFFF00;
    private static volatile int alwaysSpawnColor = 0xFFFF0000;

    private LightLevelOverlay() {}

    public static void setModule(LightLevelOverlayModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        LightLevelOverlayModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof LightLevelOverlayModule lm) {
                m = lm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled() && !HypixelSafetyManager.isDisallowed(MODULE_NAME);
        mode = m.mode.getIndex();
        renderDistance = (int) m.renderDistance.getValue();
        showSafeBlocks = m.showSafeBlocks.get();
        nightSpawnColor = m.nightSpawnColor.getColor();
        alwaysSpawnColor = m.alwaysSpawnColor.getColor();
    }

    public static boolean isEnabled() {
        if (active && HypixelSafetyManager.isDisallowed(MODULE_NAME)) return false;
        return active;
    }

    public static int getMode() { return mode; }
    public static int getRenderDistance() { return renderDistance; }
    public static boolean isShowSafeBlocks() { return showSafeBlocks; }
    public static int getNightSpawnColor() { return nightSpawnColor; }
    public static int getAlwaysSpawnColor() { return alwaysSpawnColor; }

    public static int getSpawnStatus(int blockLight, int skyLight) {
        if (blockLight <= 0) return 2;
        if (blockLight <= 7 && skyLight > 7) return 1;
        return 0;
    }
}
