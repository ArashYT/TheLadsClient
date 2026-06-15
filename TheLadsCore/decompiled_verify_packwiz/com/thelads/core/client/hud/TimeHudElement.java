/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class TimeHudElement
extends HudElement {
    public TimeHudElement() {
        this.x = 5;
        this.y = 185;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        String timeStr;
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long t = mc.level.getOverworldClockTime() % 24000L;
        long hours24 = (t / 1000L + 6L) % 24L;
        long mins = (long)((double)(t % 1000L) / 1000.0 * 60.0);
        boolean h12 = this.optBool("12-hour", false);
        boolean showLabel = this.optBool("Show label", false);
        if (h12) {
            long h = hours24 % 12L;
            if (h == 0L) {
                h = 12L;
            }
            String ampm = hours24 < 12L ? "AM" : "PM";
            timeStr = String.format("%d:%02d %s", h, mins, ampm);
        } else {
            timeStr = String.format("%02d:%02d", hours24, mins);
        }
        this.drawCenteredText(g, (String)(showLabel ? "Time " + timeStr : timeStr));
    }
}

