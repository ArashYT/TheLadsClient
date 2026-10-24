package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ShulkerBulletHighlightModule;

public final class ShulkerBulletHighlight {
    public static final String MODULE_NAME = "ShulkerBulletHighlight";
    private static ShulkerBulletHighlightModule module;
    private static volatile boolean active;
    private static volatile int bulletColor = 0xFFFF0055;
    private static volatile boolean glowEffect = true;

    private ShulkerBulletHighlight() {}

    public static void setModule(ShulkerBulletHighlightModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        ShulkerBulletHighlightModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof ShulkerBulletHighlightModule sm) {
                m = sm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled() && !HypixelSafetyManager.isDisallowed(MODULE_NAME);
        bulletColor = m.bulletColor.getColor();
        glowEffect = m.glowEffect.get();
    }

    public static boolean isEnabled() {
        if (active && HypixelSafetyManager.isDisallowed(MODULE_NAME)) return false;
        return active;
    }

    public static int getBulletColor() { return bulletColor; }
    public static boolean isGlowEffect() { return glowEffect; }
}
