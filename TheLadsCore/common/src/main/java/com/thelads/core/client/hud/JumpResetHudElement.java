package com.thelads.core.client.hud;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.HudSettings;

/**
 * Lunar & Feather parity Jump-Reset / Knockback Trainer HUD element.
 * Measures and displays jump timing relative to combat damage ticks.
 * Visual-only.
 */
public class JumpResetHudElement extends HudElement {
    public static final String MODULE_NAME = "JumpReset";

    public enum Rating {
        PERFECT("Perfect", 0xFF55FF55),
        LATE("Late", 0xFFFFAA00),
        EARLY("Early", 0xFFFF8800),
        MISSED("Missed", 0xFFFF5555),
        NONE("Ready", 0xFFAAAAAA);

        public final String label;
        public final int color;
        Rating(String label, int color) { this.label = label; this.color = color; }
    }

    private static Rating lastRating = Rating.NONE;
    private static long lastTimingMs = 0;
    private static int streak = 0;
    private static long lastDamageTimestamp = 0;
    private static boolean jumpedForCurrentDamage = false;

    public static void onPlayerDamaged() {
        lastDamageTimestamp = System.currentTimeMillis();
        jumpedForCurrentDamage = false;
    }

    public static void onPlayerJumped() {
        if (lastDamageTimestamp <= 0) return;
        long delta = System.currentTimeMillis() - lastDamageTimestamp;
        if (delta <= 500 && !jumpedForCurrentDamage) {
            jumpedForCurrentDamage = true;
            lastTimingMs = delta;
            if (delta <= 110) {
                lastRating = Rating.PERFECT;
                streak++;
            } else if (delta <= 250) {
                lastRating = Rating.LATE;
                streak = 0;
            } else {
                lastRating = Rating.MISSED;
                streak = 0;
            }
        }
    }

    public JumpResetHudElement() {
        useOrganizedDefaults();
    }

    @Override
    public void prepareRender(LadsGraphics graphics, boolean editor) {
        width = 115;
        height = 20;
    }

    @Override
    public void render(LadsGraphics graphics) {
        if (graphics == null) return;
        drawBackground(graphics);

        boolean shadow = HudSettings.getInstance().isTextShadow();
        Rating rating = lastRating;
        long timing = lastTimingMs;
        int currentStreak = streak;

        // In editor preview
        if (rating == Rating.NONE) {
            rating = Rating.PERFECT;
            timing = 42;
            currentStreak = 3;
        }

        String label = "JR: ";
        int textCol = resolveColor();
        graphics.drawText(label, x + 4, y + 5, textCol, shadow);
        int lx = x + 4 + graphics.textWidth(label);

        graphics.drawText(rating.label, lx, y + 5, rating.color, shadow);
        lx += graphics.textWidth(rating.label);

        String detail = " (+" + timing + "ms)";
        graphics.drawText(detail, lx, y + 5, 0xFFDDDDDD, shadow);

        if (currentStreak > 1) {
            String strText = " [" + currentStreak + "]";
            int stW = graphics.textWidth(strText);
            graphics.drawText(strText, x + width - stW - 3, y + 5, 0xFFFFDD44, shadow);
        }
    }
}
