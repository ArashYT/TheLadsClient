package com.thelads.smp.features.arise.nb5.client;

import com.thelads.smp.features.arise.nb5.AriseNB5Config;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Renders the clean-room ParCool-style Stamina HUD bar based on the owner's Arise SMP configuration.
 */
public final class StaminaHudRenderer {
    private static float displayStamina = 1000.0f;
    private static float alpha = 0.0f;

    private StaminaHudRenderer() {}

    public static void render(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enabled || !config.staminaEnabled || "Hide".equalsIgnoreCase(config.staminaHudType)) {
            return;
        }
        if (!ParkourClientController.isParkourAllowed()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden())) return;
        if (mc.player.isCreative() && config.infiniteStaminaCreative) return;

        int currentStamina = ParkourClientController.getStamina();
        boolean exhausted = ParkourClientController.isExhausted();

        // Smoothly interpolate display stamina
        float delta = deltaTracker.getGameTimeDeltaTicks();
        displayStamina += (currentStamina - displayStamina) * Math.min(1.0f, delta * 0.35f);

        // Alpha fade logic: hide when full if configured
        boolean shouldShow = !config.hideHudIfStaminaFull || currentStamina < config.maxStamina || exhausted;
        if (shouldShow) {
            alpha = Math.min(1.0f, alpha + delta * 0.2f);
        } else {
            alpha = Math.max(0.0f, alpha - delta * 0.1f);
        }

        if (alpha <= 0.01f) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth = 72;
        int barHeight = 4;

        int midX = screenWidth / 2;
        int x;
        if ("Left".equalsIgnoreCase(config.alignH)) {
            x = midX - 91 + config.offsetHLightHud;
        } else {
            x = midX + 91 - barWidth + config.offsetHLightHud;
        }

        int y;
        if ("Top".equalsIgnoreCase(config.alignV)) {
            y = 10 + config.offsetVLightHud;
        } else {
            y = screenHeight - 28 + config.offsetVLightHud;
        }

        int alphaBits = (int) (alpha * 255.0f) << 24;

        // Background track + border
        int bgColor = alphaBits | 0x00111111;
        int borderColor = alphaBits | 0x00333333;

        extractor.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, borderColor);
        extractor.fill(x, y, x + barWidth, y + barHeight, bgColor);

        // Fill bar
        float ratio = Math.clamp(displayStamina / (float) config.maxStamina, 0.0f, 1.0f);
        int fillWidth = (int) (ratio * barWidth);

        if (fillWidth > 0) {
            int barRgb;
            if (exhausted || ratio < 0.2f) {
                // Flash / pulsating red when exhausted or critically low
                boolean pulse = (mc.player.tickCount % 10) < 5;
                barRgb = pulse ? 0x00E74C3C : 0x00C0392B;
            } else if (ratio < 0.5f) {
                // Amber/orange
                barRgb = 0x00F39C12;
            } else {
                // Vibrant green/cyan
                barRgb = 0x002ECC71;
            }
            int fillColor = alphaBits | barRgb;
            extractor.fill(x, y, x + fillWidth, y + barHeight, fillColor);
        }
    }
}
