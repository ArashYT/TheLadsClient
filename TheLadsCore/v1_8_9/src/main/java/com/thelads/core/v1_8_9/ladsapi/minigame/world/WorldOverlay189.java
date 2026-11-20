package com.thelads.core.v1_8_9.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Screen-space part of fx (tint, vignette, flash) and the camera's letterbox bars, then the waypoint markers. All GUI quads. */
final class WorldOverlay189 {
    private static final ResourceLocation VIGNETTE = new ResourceLocation("theladscore", "textures/gui/mg_vignette.png");

    private WorldOverlay189() {}

    static void draw(Minecraft mc, boolean markers) {
        long now = Minigame.now();
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableLighting();
        FxState.Overlay tint = Minigame.fx.tint(now);
        if (tint != null && tint.alpha() > 0.003) Gui.drawRect(0, 0, w, h, argb(tint.rgb(), tint.alpha()));
        FxState.Overlay vignette = Minigame.fx.vignette(now);
        if (vignette != null && vignette.alpha() > 0.003) {
            mc.getTextureManager().bindTexture(VIGNETTE);
            GlStateManager.enableTexture2D();
            GlStateManager.color((vignette.rgb() >> 16 & 255) / 255f, (vignette.rgb() >> 8 & 255) / 255f, (vignette.rgb() & 255) / 255f,
                (float) Math.min(1, vignette.alpha()));
            Gui.drawScaledCustomSizeModalRect(0, 0, 0, 0, 256, 256, w, h, 256, 256);
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
        FxState.Overlay flash = Minigame.fx.flash(now);
        if (flash != null && flash.alpha() > 0.003) Gui.drawRect(0, 0, w, h, argb(flash.rgb(), flash.alpha()));
        double bars = Minigame.camera.barsAt(now);
        if (bars > 0) {
            int bar = (int) Math.round(h * CameraState.BARS_HEIGHT / 2 * bars);
            Gui.drawRect(0, 0, w, bar, 0xFF000000);
            Gui.drawRect(0, h - bar, w, h, 0xFF000000);
        }
        if (markers) WaypointRender189.draw(mc);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    static int argb(int rgb, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return a << 24 | rgb & 0xFFFFFF;
    }
}
