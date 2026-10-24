package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Feather-parity Damage Indicator HUD element.
 * Displays target entity name, health bar / hearts, numeric health counter,
 * and maintains active floating damage numbers.
 * GATED: Shows enemy health.
 */
public class DamageIndicatorHudElement extends HudElement {
    public static final String MODULE_NAME = "DamageIndicator";

    public record FloatingDamage(float amount, long spawnTime, double worldX, double worldY, double worldZ) {}

    private static final List<FloatingDamage> activeNumbers = new ArrayList<>();

    public static synchronized void addDamageNumber(float amount, double x, double y, double z) {
        activeNumbers.add(new FloatingDamage(amount, System.currentTimeMillis(), x, y, z));
    }

    public static synchronized List<FloatingDamage> getActiveNumbers(long maxDurationMs) {
        long now = System.currentTimeMillis();
        Iterator<FloatingDamage> it = activeNumbers.iterator();
        while (it.hasNext()) {
            if (now - it.next().spawnTime() > maxDurationMs) it.remove();
        }
        return new ArrayList<>(activeNumbers);
    }

    public DamageIndicatorHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 110;
        height = 24;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        LadsGameBridge bridge = LadsGameBridge.get();
        LadsGameBridge.EntityTargetInfo target = bridge != null ? bridge.getTargetEntityInfo() : null;

        String name;
        float hp, maxHp;
        if (target != null && target.maxHealth() > 0) {
            name = target.name();
            hp = Math.max(0, target.health());
            maxHp = target.maxHealth();
        } else {
            // In editor or when no target is present
            name = "Player";
            hp = 16.0f;
            maxHp = 20.0f;
        }

        drawBackground(graphics);

        boolean shadow = HudSettings.getInstance().isTextShadow();
        int textCol = resolveColor();
        String title = name;
        if (graphics.textWidth(title) > width - 8) {
            title = title.substring(0, Math.min(title.length(), 10)) + "..";
        }
        graphics.drawText(title, x + 4, y + 3, textCol, shadow);

        String hpStr = String.format(java.util.Locale.ROOT, "%.1f/%.0f", hp, maxHp);
        int hpWidth = graphics.textWidth(hpStr);
        graphics.drawText(hpStr, x + width - hpWidth - 4, y + 3, 0xFFFF5555, shadow);

        // Health bar
        int barX = x + 4, barY = y + 14, barW = width - 8, barH = 5;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF400000); // bar background
        float ratio = maxHp > 0 ? Math.min(1.0f, Math.max(0.0f, hp / maxHp)) : 0;
        int fillW = (int) (barW * ratio);
        if (fillW > 0) {
            int barColor = ratio > 0.5f ? 0xFF55FF55 : (ratio > 0.25f ? 0xFFFFAA00 : 0xFFFF3333);
            graphics.fill(barX, barY, barX + fillW, barY + barH, barColor);
        }
    }
}
