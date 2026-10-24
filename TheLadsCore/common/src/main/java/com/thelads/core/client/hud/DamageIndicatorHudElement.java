package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;
import java.util.Locale;

/**
 * Damage Indicator (Feather): the health of the mob or player you look at, or last looked at for 3 seconds (HudExtras.target*, written
 * by each version's tick). Its floating damage numbers are DamageNumbers. Gated: other players' exact health is not shown by vanilla.
 */
public class DamageIndicatorHudElement extends HudElement {
    private boolean editor, shown;
    private String name = "", health = "";
    private float ratio;
    private int cachedHealth = -1, cachedMax = -1, cachedFormat = -1;

    public DamageIndicatorHudElement() {
        width = 120;
        height = 24;
    }

    @Override
    public void prepareRender(LadsGraphics g, boolean editor) {
        this.editor = editor;
        shown = HudExtras.targetId != -1 && System.currentTimeMillis() < HudExtras.targetUntil;
        float hp = shown ? HudExtras.targetHealth : 16, max = shown ? HudExtras.targetMax : 20;
        name = shown ? HudExtras.targetName : "Player";
        int format = optCycle("Health Format", 0), hpBits = Float.floatToIntBits(hp), maxBits = Float.floatToIntBits(max);
        if (hpBits != cachedHealth || maxBits != cachedMax || format != cachedFormat) {
            health = format(hp, max, format);
            cachedHealth = hpBits;
            cachedMax = maxBits;
            cachedFormat = format;
        }
        ratio = max > 0 ? Math.max(0, Math.min(1, hp / max)) : 0;
        width = Math.max(90, g.textWidth(name) + g.textWidth(health) + 16);
        height = optBool("Health Bar", true) ? 24 : 15;
    }

    /** "8.5 ❤" (hearts), "17/20" (points) or "85%". */
    static String format(float hp, float max, int format) {
        return switch (format) {
            case 1 -> (int) Math.ceil(hp) + "/" + (int) Math.ceil(max);
            case 2 -> (max > 0 ? Math.round(hp * 100 / max) : 0) + "%";
            default -> String.format(Locale.ROOT, "%.1f ", hp / 2) + DamageNumbers.HEART;
        };
    }

    @Override
    public void render(LadsGraphics g) {
        if (!shown && !editor) return;
        drawBackground(g);
        boolean shadow = HudSettings.getInstance().isTextShadow();
        g.drawText(name, x + 4, y + 4, resolveColor(), shadow);
        g.drawText(health, x + width - g.textWidth(health) - 4, y + 4, 0xFFFF5555, shadow);
        if (height < 24) return;
        int barX = x + 4, barY = y + 15, barW = width - 8;
        g.fill(barX, barY, barX + barW, barY + 4, 0xFF400000);
        int fill = (int) (barW * ratio);
        if (fill > 0) g.fill(barX, barY, barX + fill, barY + 4, ratio > 0.5f ? 0xFF55FF55 : ratio > 0.25f ? 0xFFFFAA00 : 0xFFFF3333);
    }
}
