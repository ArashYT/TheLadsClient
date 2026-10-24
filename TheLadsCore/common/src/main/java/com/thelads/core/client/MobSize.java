package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.MobSizeModule;

public final class MobSize {
    public static final String MODULE_NAME = "MobSize";
    private static MobSizeModule module;
    private static volatile boolean active;
    private static volatile float mobScale = 1.0f;
    private static volatile boolean scalePlayers;

    private MobSize() {}

    public static void setModule(MobSizeModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        MobSizeModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof MobSizeModule mm) {
                m = mm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled() && !HypixelSafetyManager.isDisallowed(MODULE_NAME);
        mobScale = (float) m.mobScale.getValue();
        scalePlayers = m.scalePlayers.get();
    }

    public static boolean isEnabled() {
        if (active && HypixelSafetyManager.isDisallowed(MODULE_NAME)) return false;
        return active;
    }

    public static float getScale(boolean isPlayer) {
        if (!isEnabled()) return 1.0f;
        if (isPlayer && !scalePlayers) return 1.0f;
        return mobScale;
    }
}
