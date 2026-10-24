package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.OverlayTweaksModule;

public final class OverlayTweaks {
    public static final String MODULE_NAME = "Overlay Tweaks";
    private static OverlayTweaksModule module;
    private static volatile boolean active;
    private static volatile float fireOpacity = 1.0f;
    private static volatile float fireHeight = 1.0f;
    private static volatile float pumpkinOpacity = 1.0f;
    private static volatile float portalOpacity = 1.0f;
    private static volatile float waterFogOpacity = 1.0f;
    private static volatile boolean clearWater;
    private static volatile boolean fireResistanceFade = true;

    private OverlayTweaks() {}

    public static void setModule(OverlayTweaksModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        OverlayTweaksModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof OverlayTweaksModule otm) {
                m = otm;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        fireOpacity = (float) (m.fireOpacity.getValue() / 100.0);
        fireHeight = (float) (m.fireHeight.getValue() / 100.0);
        pumpkinOpacity = (float) (m.pumpkinOpacity.getValue() / 100.0);
        portalOpacity = (float) (m.portalOpacity.getValue() / 100.0);
        waterFogOpacity = (float) (m.waterFogOpacity.getValue() / 100.0);
        clearWater = m.clearWater.get();
        fireResistanceFade = m.fireResistanceFade.get();
    }

    public static boolean isEnabled() { return active; }

    public static float getFireOpacity(boolean hasFireResistance) {
        if (!active) return 1.0f;
        if (hasFireResistance && fireResistanceFade) return 0.0f;
        return fireOpacity;
    }

    public static float getFireHeight() { return active ? fireHeight : 1.0f; }
    public static float getPumpkinOpacity() { return active ? pumpkinOpacity : 1.0f; }
    public static float getPortalOpacity() { return active ? portalOpacity : 1.0f; }
    public static float getWaterFogOpacity() { return active ? waterFogOpacity : 1.0f; }
    public static boolean isClearWater() { return active && clearWater; }
    public static boolean shouldFadeFireResistance() { return active && fireResistanceFade; }
}
