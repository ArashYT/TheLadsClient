package com.thelads.smp.features.arise.nb6.client;

import com.thelads.smp.features.arise.nb6.AriseNB6Config;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * HUD renderer for:
 * 1. The active Fishing Minigame tension meter
 * 2. The Hold-to-Charge Cast Power Bar
 */
public final class FishingMinigameHud {
    private FishingMinigameHud() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // 1. Render Hold-to-Charge Cast Bar
        renderCastBar(graphics, client, screenWidth, screenHeight);

        // 2. Render Active Fishing Minigame
        if (FishingMinigameClient.isActive()) {
            renderMinigame(graphics, client, screenWidth, screenHeight);
        }
    }

    private static void renderCastBar(GuiGraphicsExtractor graphics, Minecraft client, int screenWidth, int screenHeight) {
        Player player = client.player;
        if (player == null || !player.isUsingItem()) {
            return;
        }
        if (!player.getUseItem().is(Items.FISHING_ROD) || !AriseNB6Config.get().holdToCast) {
            return;
        }

        int ticksUsing = player.getTicksUsingItem();
        float maxTicks = Math.max(10.0f, AriseNB6Config.get().maxCastChargeSeconds * 20.0f);
        float ratio = Math.min(1.0f, ticksUsing / maxTicks);

        int barWidth = 70;
        int barHeight = 5;
        int x = screenWidth / 2 - barWidth / 2;
        int y = screenHeight / 2 + 18;

        // Background
        graphics.fill(x - 2, y - 2, x + barWidth + 2, y + barHeight + 2, 0xCC111111);
        // Inner track
        graphics.fill(x, y, x + barWidth, y + barHeight, 0xFF333333);
        // Filled power
        int filled = (int) (barWidth * ratio);
        int color = (ratio >= 1.0f) ? 0xFF00FF88 : 0xFF33AAFF;
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + barHeight, color);
        }

        // Percentage label
        String label = "Cast: " + (int) (ratio * 100) + "%";
        graphics.centeredText(client.font, label, screenWidth / 2, y - 10, 0xFFE0E0E0);
    }

    private static void renderMinigame(GuiGraphicsExtractor graphics, Minecraft client, int screenWidth, int screenHeight) {
        int meterWidth = 18;
        int meterHeight = 100;
        int x = screenWidth / 2 + 70;
        int y = screenHeight / 2 - 50;

        // Outer brass/wooden border
        graphics.fill(x - 3, y - 3, x + meterWidth + 3, y + meterHeight + 3, 0xDD2A1E14);
        graphics.fill(x - 2, y - 2, x + meterWidth + 2, y + meterHeight + 2, 0xFFD4AF37);
        // Deep water background track
        graphics.fill(x, y, x + meterWidth, y + meterHeight, 0xDD0D1B2A);

        // Player Catch Bar (green zone)
        float barPos = FishingMinigameClient.getBarPos();
        float barH = FishingMinigameClient.getBarHeight();
        int drawBarY = y + meterHeight - (int) ((barPos + barH) * (meterHeight / 100.0f));
        int drawBarH = (int) (barH * (meterHeight / 100.0f));

        graphics.fill(x + 1, drawBarY, x + meterWidth - 1, drawBarY + drawBarH, 0xAA2EC4B6);
        graphics.fill(x + 2, drawBarY + 1, x + meterWidth - 2, drawBarY + drawBarH - 1, 0xCC38B000);

        // Swimming Fish Target
        float fishPos = FishingMinigameClient.getFishPos();
        int fishY = y + meterHeight - (int) (fishPos * (meterHeight / 100.0f));

        // Fish icon marker (horizontal needle/fish silhouette)
        graphics.fill(x + 2, fishY - 3, x + meterWidth - 2, fishY + 3, 0xFFFF7900);
        graphics.fill(x + 4, fishY - 1, x + meterWidth - 4, fishY + 1, 0xFFFFFFFF);

        // Progress bar (vertical stripe on right)
        float progress = FishingMinigameClient.getProgress();
        int progX = x + meterWidth + 5;
        int progWidth = 6;
        graphics.fill(progX - 1, y - 1, progX + progWidth + 1, y + meterHeight + 1, 0xCC111111);
        graphics.fill(progX, y, progX + progWidth, y + meterHeight, 0xFF222222);

        int progH = (int) (progress * (meterHeight / 100.0f));
        int progColor;
        if (progress > 70.0f) {
            progColor = 0xFF38B000;
        } else if (progress > 35.0f) {
            progColor = 0xFFFFB703;
        } else {
            progColor = 0xFFD90429;
        }

        if (progH > 0) {
            graphics.fill(progX, y + meterHeight - progH, progX + progWidth, y + meterHeight, progColor);
        }

        // Catch title
        graphics.centeredText(client.font, "§6§lCatch!", x + meterWidth / 2, y - 13, 0xFFFFAA00);
        // Instruction hint
        graphics.centeredText(client.font, "§7[Hold Click / Space]", x + meterWidth / 2, y + meterHeight + 5, 0xFFAAAAAA);
    }
}
