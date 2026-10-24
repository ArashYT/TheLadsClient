package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ItemDespawnFlashModule;

public final class ItemDespawnFlash {
    public static final String MODULE_NAME = "Item Despawn Flash";
    private static ItemDespawnFlashModule module;
    private static volatile boolean active;
    private static volatile int thresholdSeconds = 30;
    private static volatile int flashSpeed = 2;
    private static volatile int blinkColor = 0xFFFF5555;

    private ItemDespawnFlash() {}

    public static void setModule(ItemDespawnFlashModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        ItemDespawnFlashModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof ItemDespawnFlashModule im) {
                m = im;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        thresholdSeconds = (int) m.thresholdSeconds.getValue();
        flashSpeed = (int) m.flashSpeed.getValue();
        blinkColor = m.blinkColor.getColor();
    }

    public static boolean isEnabled() { return active; }

    public static boolean shouldFlash(int ageTicks) {
        if (!active) return false;
        int thresholdTicks = 6000 - (thresholdSeconds * 20);
        return ageTicks >= thresholdTicks;
    }

    public static boolean isFlashOn(int ageTicks) {
        if (!shouldFlash(ageTicks)) return false;
        int period = Math.max(2, 20 / Math.max(1, flashSpeed * 2));
        return (ageTicks / period) % 2 == 0;
    }

    public static int getBlinkColor() { return blinkColor; }
}
