package com.thelads.core.client.hud;

import com.thelads.core.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Collection;

public class PotionHudElement extends HudElement {
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
        Collection<MobEffectInstance> effects = mc.player.getActiveEffects();
        boolean shadow = HudSettings.getInstance().isTextShadow();

        if (effects.isEmpty()) {
            // Invisible by default when no effects are active; opt in to show "No effects".
            if (optBool("Show when empty", false)) {
                this.height = 16;
                drawBackground(g);
                drawCenteredText(g, "No effects");
            }
            return;
        }

        boolean showDuration = optBool("Show duration", true);
        this.height = effects.size() * 11 + 5;
        drawBackground(g);
        int line = 0;
        for (MobEffectInstance fx : effects) {
            String name = fx.getEffect().value().getDisplayName().getString();
            int amp = fx.getAmplifier();
            if (amp > 0) {
                name += " " + (amp + 1);
            }
            String text = showDuration ? (name + " " + formatTime(fx.getDuration())) : name;
            g.text(mc.font, text, this.x + 4, this.y + 3 + line * 11, resolveColor(), shadow);
            line++;
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
