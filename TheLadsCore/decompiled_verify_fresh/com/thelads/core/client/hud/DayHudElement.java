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

public class DayHudElement
extends HudElement {
    public DayHudElement() {
        this.x = 5;
        this.y = 165;
        this.width = 70;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long day = mc.level.getOverworldClockTime() / 24000L;
        boolean showLabel = this.optBool("Show label", true);
        this.drawCenteredText(g, (String)(showLabel ? "Day " + day : String.valueOf(day)));
    }
}

