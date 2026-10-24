package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.RenderTweaksModule;

public final class RenderTweaks {
    public static final String MODULE_NAME = "Render Tweaks";
    private static RenderTweaksModule module;
    private static volatile boolean active;
    private static volatile boolean entityShadows = true;
    private static volatile boolean hideLightning;
    private static volatile boolean hideFishingLine;
    private static volatile boolean removeElderGuardianCurse = true;

    private RenderTweaks() {}

    public static void setModule(RenderTweaksModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        RenderTweaksModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof RenderTweaksModule rtm) {
                m = rtm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        entityShadows = m.entityShadows.get();
        hideLightning = m.hideLightning.get();
        hideFishingLine = m.hideFishingLine.get();
        removeElderGuardianCurse = m.removeElderGuardianCurse.get();
        BobbingTweaks.setDisconnectEnabled(active && m.bobbingDisconnect.get());
    }

    public static boolean isEnabled() { return active; }
    public static boolean isEntityShadowsEnabled() { return !active || entityShadows; }
    public static boolean isHideLightning() { return active && hideLightning; }
    public static boolean isHideFishingLine() { return active && hideFishingLine; }
    public static boolean isElderGuardianCurseHidden() { return active && removeElderGuardianCurse; }
}
