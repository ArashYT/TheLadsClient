package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.food.FoodData;

public class HungerHudElement extends HudElement {
    public HungerHudElement() {
        this.x = 5;
        this.y = 225;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        FoodData food = mc.player.getFoodData();
        int level = food.getFoodLevel();
        boolean showLabel   = optBool("Show label", true);
        boolean showSaturat = optBool("Show saturation", false);

        String text;
        if (showSaturat) {
            int sat = (int) food.getSaturationLevel();
            text = (showLabel ? "Food " : "") + level + "/20 ★" + sat;
        } else {
            text = (showLabel ? "Food " : "") + level + "/20";
        }
        drawCenteredText(g, text);
    }
}
