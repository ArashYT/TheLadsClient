package com.thelads.smp.features.arise.nb2;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;

/**
 * Manages the virtual empty hand state (Hold My Beer: Empty Hand).
 */
public final class HoldMyBeerManager {
    private static volatile boolean toggled = false;
    private static volatile boolean held = false;

    private HoldMyBeerManager() {}

    public static boolean isActive() {
        if (!NB2Config.get().holdMyBeer) {
            return false;
        }
        return NB2Config.get().pressAndHold ? held : toggled;
    }

    public static void setHeld(boolean isHeld) {
        held = isHeld;
    }

    public static boolean toggle() {
        toggled = !toggled;
        return toggled;
    }

    public static boolean shouldSuppressUse(InteractionHand hand) {
        if (!isActive()) {
            return false;
        }
        if (hand == InteractionHand.OFF_HAND && !NB2Config.get().emptyOffhand) {
            return false;
        }
        return true;
    }

    @Environment(EnvType.CLIENT)
    public static void renderHudIndicator(GuiGraphicsExtractor graphics) {
        if (!isActive() || !NB2Config.get().showEmptyHandIcon) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // Position directly to the left of the hotbar
        int x = (width / 2) - 91 - 22;
        int y = height - 22;

        // Render small card with subtle accent
        graphics.fill(x, y, x + 18, y + 18, 0xAA000000);
        graphics.fill(x, y, x + 18, y + 1, 0xFF4A90E2);

        // Draw "EH" (Empty Hand) in clean small text centered
        String label = "EH";
        int textWidth = mc.font.width(label);
        graphics.text(mc.font, label, x + (18 - textWidth) / 2, y + 5, 0xFFFFFFFF, true);
    }
}
