package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HealthHudElement extends HudElement {
    public HealthHudElement() {
        this.x = 5;
        this.y = 205;
        this.width = 80;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int hp  = (int) Math.ceil(mc.player.getHealth());
        int max = (int) Math.ceil(mc.player.getMaxHealth());
        float absorb = mc.player.getAbsorptionAmount();

        int format          = optCycle("Format", 0); // 0=x/max, 1=HP x/max, 2=x, 3=Percent
        boolean showAbsorb  = optBool("Show absorption", true);

        String hpStr;
        switch (format) {
            case 1 -> hpStr = "HP " + hp + "/" + max;
            case 2 -> hpStr = String.valueOf(hp);
            case 3 -> hpStr = Math.round(100.0f * mc.player.getHealth() / mc.player.getMaxHealth()) + "%";
            default -> hpStr = hp + "/" + max;
        }

        if (showAbsorb && absorb > 0) {
            hpStr += " (+" + (int) Math.ceil(absorb) + ")";
        }
        drawCenteredText(g, hpStr);
    }
}
