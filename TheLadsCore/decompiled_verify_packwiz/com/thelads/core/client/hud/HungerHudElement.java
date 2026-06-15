/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.world.food.FoodData
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.food.FoodData;

public class HungerHudElement
extends HudElement {
    public HungerHudElement() {
        this.x = 5;
        this.y = 225;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        String text;
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        FoodData food = mc.player.getFoodData();
        int level = food.getFoodLevel();
        boolean showLabel = this.optBool("Show label", true);
        boolean showSaturat = this.optBool("Show saturation", false);
        if (showSaturat) {
            int sat = (int)food.getSaturationLevel();
            text = (showLabel ? "Food " : "") + level + "/20 \u2605" + sat;
        } else {
            text = (showLabel ? "Food " : "") + level + "/20";
        }
        this.drawCenteredText(g, text);
    }
}

