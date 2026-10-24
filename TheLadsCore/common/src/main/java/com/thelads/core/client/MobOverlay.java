package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.MobOverlayModule;

public final class MobOverlay {
    public static final String MODULE_NAME = "MobOverlay";
    private static MobOverlayModule module;
    private static volatile boolean active;
    private static volatile int hostileColor = 0xFFFF4444;
    private static volatile int passiveColor = 0xFF44FF44;
    private static volatile int playerColor = 0xFF4444FF;
    private static volatile float outlineWidth = 2.0f;
    private static volatile boolean throughWalls;

    private MobOverlay() {}

    public static void setModule(MobOverlayModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        MobOverlayModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof MobOverlayModule mm) {
                m = mm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled() && !HypixelSafetyManager.isDisallowed(MODULE_NAME);
        hostileColor = m.hostileColor.getColor();
        passiveColor = m.passiveColor.getColor();
        playerColor = m.playerColor.getColor();
        outlineWidth = (float) m.outlineWidth.getValue();
        throughWalls = m.throughWalls.get();
    }

    public static boolean isEnabled() {
        if (active && HypixelSafetyManager.isDisallowed(MODULE_NAME)) return false;
        return active;
    }

    public static int getHostileColor() { return hostileColor; }
    public static int getPassiveColor() { return passiveColor; }
    public static int getPlayerColor() { return playerColor; }
    public static float getOutlineWidth() { return outlineWidth; }
    public static boolean isThroughWalls() { return throughWalls; }

    public static int getEntityColor(boolean isPlayer, boolean isHostile) {
        if (isPlayer) return playerColor;
        if (isHostile) return hostileColor;
        return passiveColor;
    }
}
