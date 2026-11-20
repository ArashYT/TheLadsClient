package com.thelads.core.v1_20_1.ladsapi.minigame.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Screen-space part of fx (tint, vignette, flash) and the camera's letterbox bars, then the waypoint markers. */
public final class WorldOverlay1201 {
    private static final ResourceLocation VIGNETTE = new ResourceLocation("theladscore", "textures/gui/mg_vignette.png");

    private WorldOverlay1201() {}

    public static void draw(GuiGraphics g, boolean markers) {
        long now = Minigame.now();
        int w = g.guiWidth(), h = g.guiHeight();
        FxState.Overlay tint = Minigame.fx.tint(now);
        if (tint != null && tint.alpha() > 0.003) g.fill(0, 0, w, h, argb(tint.rgb(), tint.alpha()));
        FxState.Overlay vignette = Minigame.fx.vignette(now);
        if (vignette != null && vignette.alpha() > 0.003) {
            int rgb = vignette.rgb();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, (float) Math.min(1, vignette.alpha()));
            g.blit(VIGNETTE, 0, 0, w, h, 0f, 0f, 256, 256, 256, 256);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        FxState.Overlay flash = Minigame.fx.flash(now);
        if (flash != null && flash.alpha() > 0.003) g.fill(0, 0, w, h, argb(flash.rgb(), flash.alpha()));
        double bars = Minigame.camera.barsAt(now);
        if (bars > 0) {
            int bar = (int) Math.round(h * CameraState.BARS_HEIGHT / 2 * bars);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        if (markers) WaypointRender1201.drawMarkers(g, now);
    }

    static int argb(int rgb, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return a << 24 | rgb & 0xFFFFFF;
    }
}
