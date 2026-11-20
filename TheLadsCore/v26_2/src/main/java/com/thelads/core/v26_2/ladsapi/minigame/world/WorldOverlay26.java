package com.thelads.core.v26_2.ladsapi.minigame.world;

import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Screen-space part of fx (tint, vignette, flash) and the camera's letterbox bars, then the waypoint markers. */
public final class WorldOverlay26 {
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("theladscore", "textures/gui/mg_vignette.png");

    private WorldOverlay26() {}

    public static void draw(GuiGraphicsExtractor g, boolean markers) {
        long now = Minigame.now();
        int w = g.guiWidth(), h = g.guiHeight();
        FxState.Overlay tint = Minigame.fx.tint(now);
        if (tint != null && tint.alpha() > 0.003) g.fill(0, 0, w, h, argb(tint.rgb(), tint.alpha()));
        FxState.Overlay vignette = Minigame.fx.vignette(now);
        if (vignette != null && vignette.alpha() > 0.003) {
            g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, w, h, 256, 256, 256, 256, argb(vignette.rgb(), vignette.alpha()));
        }
        FxState.Overlay flash = Minigame.fx.flash(now);
        if (flash != null && flash.alpha() > 0.003) g.fill(0, 0, w, h, argb(flash.rgb(), flash.alpha()));
        double bars = Minigame.camera.barsAt(now);
        if (bars > 0) {
            int bar = (int) Math.round(h * CameraState.BARS_HEIGHT / 2 * bars);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        if (markers) WaypointRender26.drawMarkers(g, now);
    }

    static int argb(int rgb, double alpha) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return a << 24 | rgb & 0xFFFFFF;
    }
}
