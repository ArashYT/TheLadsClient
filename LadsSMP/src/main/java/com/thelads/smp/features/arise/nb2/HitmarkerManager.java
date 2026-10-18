package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

/**
 * Client-side hitmarker crosshair indicator and audio ping on successful entity attack.
 */
public final class HitmarkerManager {
    private static volatile long lastHitTimeMs = 0L;

    private HitmarkerManager() {}

    @Environment(EnvType.CLIENT)
    public static void onHit() {
        if (!NB2Config.get().hitmarker) {
            return;
        }
        lastHitTimeMs = System.currentTimeMillis();

        if (NB2Config.get().hitmarkerSound) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.playSound(SoundEvents.ARROW_HIT_PLAYER, 0.45f, 1.45f);
            }
        }
    }

    public static boolean isHitActive(long nowMs, int durationTicks) {
        if (lastHitTimeMs <= 0L) {
            return false;
        }
        long durationMs = (long) durationTicks * 50L;
        return (nowMs - lastHitTimeMs) <= durationMs;
    }

    @Environment(EnvType.CLIENT)
    public static void renderCrosshairHitmarker(GuiGraphicsExtractor graphics) {
        if (!NB2Config.get().hitmarker) {
            return;
        }
        long now = System.currentTimeMillis();
        int ticks = NB2Config.get().hitmarkerTicks;
        if (!isHitActive(now, ticks)) {
            return;
        }

        long elapsedMs = now - lastHitTimeMs;
        float progress = 1.0f - ((float) elapsedMs / (ticks * 50.0f));
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        int alpha = (int) (progress * 255.0f);
        int color = (alpha << 24) | 0x00FFFFFF;

        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;

        int gap = 3;
        int len = 4;

        // Top-left diagonal tick
        graphics.fill(cx - gap - len, cy - gap - len, cx - gap, cy - gap - len + 1, color);
        graphics.fill(cx - gap - 1, cy - gap - len, cx - gap, cy - gap, color);

        // Top-right diagonal tick
        graphics.fill(cx + gap, cy - gap - len, cx + gap + len, cy - gap - len + 1, color);
        graphics.fill(cx + gap, cy - gap - len, cx + gap + 1, cy - gap, color);

        // Bottom-left diagonal tick
        graphics.fill(cx - gap - len, cy + gap + len - 1, cx - gap, cy + gap + len, color);
        graphics.fill(cx - gap - 1, cy + gap, cx - gap, cy + gap + len, color);

        // Bottom-right diagonal tick
        graphics.fill(cx + gap, cy + gap + len - 1, cx + gap + len, cy + gap + len, color);
        graphics.fill(cx + gap, cy + gap, cx + gap + 1, cy + gap + len, color);
    }
}
