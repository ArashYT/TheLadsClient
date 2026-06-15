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

public class DirectionHudElement
extends HudElement {
    private static final String[] CARDINAL_SHORT = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] CARDINAL_LONG = new String[]{"South", "Southwest", "West", "Northwest", "North", "Northeast", "East", "Southeast"};

    public DirectionHudElement() {
        this.x = 5;
        this.y = 125;
        this.width = 90;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float yaw = mc.player.getYRot();
        yaw = (yaw % 360.0f + 360.0f) % 360.0f;
        int idx = (int)Math.round((double)yaw / 45.0) % 8;
        int format = this.optCycle("Format", 0);
        boolean longName = this.optBool("Long names", false);
        String[] cardinals = longName ? CARDINAL_LONG : CARDINAL_SHORT;
        int degrees = Math.round(yaw);
        Object text = format == 0 ? cardinals[idx] : (format == 1 ? degrees + "\u00b0" : cardinals[idx] + " (" + degrees + "\u00b0)");
        this.drawCenteredText(g, (String)text);
    }
}

