package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.TotemAnimationModule;

public final class TotemAnimation {
    public static final String MODULE_NAME = "Totem Animation";
    private static TotemAnimationModule module;
    private static volatile boolean active;
    private static volatile boolean hideTotem;
    private static volatile float scale = 1.0f;
    private static volatile float opacity = 1.0f;

    private TotemAnimation() {}

    public static void setModule(TotemAnimationModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        TotemAnimationModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof TotemAnimationModule tm) {
                m = tm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        hideTotem = m.hideAnimation.get();
        scale = (float) (m.totemScale.getValue() / 100.0);
        opacity = (float) (m.totemOpacity.getValue() / 100.0);
    }

    public static boolean isEnabled() { return active; }
    public static boolean shouldHideAnimation() { return active && hideTotem; }
    public static float getScale() { return active ? scale : 1.0f; }
    public static float getOpacity() { return active ? opacity : 1.0f; }
}
