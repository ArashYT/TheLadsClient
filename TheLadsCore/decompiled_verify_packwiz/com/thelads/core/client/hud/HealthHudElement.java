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

public class HealthHudElement
extends HudElement {
    public HealthHudElement() {
        this.x = 5;
        this.y = 205;
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
        int hp = (int)Math.ceil(mc.player.getHealth());
        int max = (int)Math.ceil(mc.player.getMaxHealth());
        float absorb = mc.player.getAbsorptionAmount();
        int format = this.optCycle("Format", 0);
        boolean showAbsorb = this.optBool("Show absorption", true);
        Object hpStr = switch (format) {
            case 1 -> "HP " + hp + "/" + max;
            case 2 -> String.valueOf(hp);
            case 3 -> Math.round(100.0f * mc.player.getHealth() / mc.player.getMaxHealth()) + "%";
            default -> hp + "/" + max;
        };
        if (showAbsorb && absorb > 0.0f) {
            hpStr = (String)hpStr + " (+" + (int)Math.ceil(absorb) + ")";
        }
        this.drawCenteredText(g, (String)hpStr);
    }
}

