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

public class XpHudElement
extends HudElement {
    public XpHudElement() {
        this.x = 5;
        this.y = 245;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int level = mc.player.experienceLevel;
        int pct = Math.round(mc.player.experienceProgress * 100.0f);
        int format = this.optCycle("Format", 0);
        this.drawCenteredText(g, switch (format) {
            case 1 -> pct + "%";
            case 2 -> "Lvl " + level + " \u00b7 " + pct + "%";
            default -> "Lvl " + level;
        });
    }
}

