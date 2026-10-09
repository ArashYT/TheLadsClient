package com.thelads.core.v1_20_1.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_20_1.feature.NativeAutohide1201;
import com.thelads.core.v1_20_1.feature.NativeKillBanner1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** What the Lads HUD does around vanilla's in-game GUI each frame (called from GameRendererHudMixin). */
public final class HudPipeline1201 {
    private static long lastFailure;

    private HudPipeline1201() {}

    public static void beforeGui(GuiGraphics graphics) {
        NativeAutohide1201.scopeOpacity = 1;
    }

    /**
     * A new layer in front of everything drawn so far, the way 26.x paints in call order. 1.20.1 depth-tests its GUI: vanilla's hotbar
     * items sit 60 to 110 in front of the plain fills drawn over them, so a Lads plate or the Kill Banner's shadow disc would let the
     * items and the text under it show through. Vanilla clears the depth buffer right after the GUI for the screens; the Lads layers do
     * it themselves first. What is batched is drawn before the clear.
     */
    private static void layer(GuiGraphics graphics) {
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);
    }

    /** After the whole vanilla GUI (and the mods' HUDs drawn with it): the Lads HUD, then the kill banner above it. */
    public static void afterGui(GuiGraphics graphics) {
        layer(graphics);
        try {
            NativeAutohide1201.renderLadsHud(graphics);
        } catch (RuntimeException failure) {
            // Once every few seconds, not once per frame.
            long now = System.nanoTime();
            if (now - lastFailure > 5_000_000_000L) {
                lastFailure = now;
                Hud1201.LOG.error("Lads HUD failed to render", failure);
            }
        } finally {
            NativeAutohide1201.scopeOpacity = 1;
        }
        layer(graphics);
        com.thelads.core.v1_20_1.hud.overlay.OverlayFeatures1201.hud(graphics);
        layer(graphics);
        NativeKillBanner1201.render(graphics);
    }
}
