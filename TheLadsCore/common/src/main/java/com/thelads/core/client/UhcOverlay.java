package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.UhcOverlayModule;

public final class UhcOverlay {
    public static final String MODULE_NAME = "UHC Overlay";
    private static UhcOverlayModule module;
    private static volatile boolean active;
    private static volatile float scaleFactor = 1.3f;
    private static volatile boolean highlightGoldenApples = true;
    private static volatile boolean highlightHeads = true;
    private static volatile boolean highlightOres = true;
    private static volatile int borderColor = 0xFFFFD700;

    private UhcOverlay() {}

    public static void setModule(UhcOverlayModule m) {
        module = m;
        refresh();
    }

    public static void refresh() {
        UhcOverlayModule m = null;
        try {
            var mgr = ModuleManager.getInstance();
            if (mgr != null && mgr.getModule(MODULE_NAME) instanceof UhcOverlayModule um) {
                m = um;
            }
        } catch (Throwable ignored) {}
        if (m == null) m = module;
        if (m == null) {
            active = false;
            return;
        }
        active = m.isEnabled();
        scaleFactor = (float) m.scaleFactor.getValue();
        highlightGoldenApples = m.highlightGoldenApples.get();
        highlightHeads = m.highlightHeads.get();
        highlightOres = m.highlightOres.get();
        borderColor = m.borderColor.getColor();
    }

    public static boolean isEnabled() { return active; }

    public static boolean isHighlightedItem(String itemId) {
        if (!active || itemId == null) return false;
        if (highlightGoldenApples && (itemId.contains("golden_apple") || itemId.contains("appleGold"))) return true;
        if (highlightHeads && (itemId.contains("skull") || itemId.contains("player_head") || itemId.contains("head"))) return true;
        if (highlightOres && (itemId.contains("ore") || itemId.contains("raw_gold") || itemId.contains("raw_iron"))) return true;
        return false;
    }

    public static float getScaleFactor() { return active ? scaleFactor : 1.0f; }
    public static int getBorderColor() { return borderColor; }
}
