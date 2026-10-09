package com.thelads.core.v1_20_1.hud.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** TabList/TabTweaks, Nametags, PingView, ConnectionStatus, SignalLoss + commands, Crosshair Tweaks and Paperdoll (hud-overlay group). */
public final class OverlayFeatures1201 {
    private OverlayFeatures1201() {}

    public static void register() {
        Crosshair1201.register();
        PaperDoll1201.register();
        TabTweaks1201.register();
        SignalLoss1201.register();
    }

    /** The group's HUD, drawn by HudPipeline1201 right after the whole vanilla GUI (the same call on plain Fabric and under Forge). */
    public static void hud(GuiGraphics graphics) {
        SignalLoss1201.render(graphics);
    }

    /** GameRenderer.render right after the HUD pass, before overlays and screens (OverlayGameRendererMixin); every frame. */
    public static void afterHud(boolean renderedWorld) {
        Minecraft mc = Minecraft.getInstance();
        if (!renderedWorld || mc.level == null || mc.player == null) return;
        if (!mc.options.hideGui || !Crosshair1201.active()) return;
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        Crosshair1201.renderHiddenHud(graphics);
        graphics.flush();
    }
}
