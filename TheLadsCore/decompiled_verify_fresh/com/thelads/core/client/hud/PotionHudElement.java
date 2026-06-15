/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectInstance
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.config.HudSettings;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public class PotionHudElement
extends HudElement {
    public PotionHudElement() {
        this.x = 5;
        this.y = 305;
        this.width = 120;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Collection effects = mc.player.getActiveEffects();
        boolean shadow = HudSettings.getInstance().isTextShadow();
        if (effects.isEmpty()) {
            if (this.optBool("Show when empty", false)) {
                this.height = 16;
                this.drawBackground(g);
                this.drawCenteredText(g, "No effects");
            }
            return;
        }
        boolean showDuration = this.optBool("Show duration", true);
        this.height = effects.size() * 11 + 5;
        this.drawBackground(g);
        int line = 0;
        for (MobEffectInstance fx : effects) {
            Object name = ((MobEffect)fx.getEffect().value()).getDisplayName().getString();
            int amp = fx.getAmplifier();
            if (amp > 0) {
                name = (String)name + " " + (amp + 1);
            }
            String text = showDuration ? (String)name + " " + PotionHudElement.formatTime(fx.getDuration()) : name;
            g.text(mc.font, text, this.x + 4, this.y + 3 + line * 11, this.resolveColor(), shadow);
            ++line;
        }
    }

    private static String formatTime(int ticks) {
        if (ticks < 0) {
            return "--:--";
        }
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}

