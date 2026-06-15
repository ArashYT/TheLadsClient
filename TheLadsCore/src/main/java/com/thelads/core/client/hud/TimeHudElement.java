package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class TimeHudElement extends HudElement {
    public TimeHudElement() {
        this.x = 5;
        this.y = 185;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long t = mc.level.getOverworldClockTime() % 24000L;
        long hours24 = ((t / 1000L) + 6L) % 24L;
        long mins    = (long) ((t % 1000L) / 1000.0 * 60.0);

        boolean h12       = optBool("12-hour", false);
        boolean showLabel = optBool("Show label", false);

        String timeStr;
        if (h12) {
            long h = hours24 % 12;
            if (h == 0) h = 12;
            String ampm = hours24 < 12 ? "AM" : "PM";
            timeStr = String.format("%d:%02d %s", h, mins, ampm);
        } else {
            timeStr = String.format("%02d:%02d", hours24, mins);
        }

        drawCenteredText(g, showLabel ? "Time " + timeStr : timeStr);
    }
}
