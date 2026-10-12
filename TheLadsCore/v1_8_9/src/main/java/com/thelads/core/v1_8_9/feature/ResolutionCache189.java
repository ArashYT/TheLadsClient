package com.thelads.core.v1_8_9.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public final class ResolutionCache189 {
    private static ScaledResolution cached;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static int lastGuiScale = -1;
    private static boolean lastUnicode = false;
    private static int lastExtraRows = -1;

    private ResolutionCache189() {}

    public static ScaledResolution get(Minecraft mc) {
        if (mc == null) return null;
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        int scale = mc.gameSettings != null ? mc.gameSettings.guiScale : 0;
        boolean unicode = mc.isUnicode();
        int extra = Borderless189.extraRows();

        if (cached == null
                || w != lastWidth
                || h != lastHeight
                || scale != lastGuiScale
                || unicode != lastUnicode
                || extra != lastExtraRows) {
            cached = new ScaledResolution(mc);
            lastWidth = w;
            lastHeight = h;
            lastGuiScale = scale;
            lastUnicode = unicode;
            lastExtraRows = extra;
        }
        return cached;
    }
}
