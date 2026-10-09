package com.thelads.core.v1_20_1.hud.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** TabList/TabTweaks, Nametags, PingView, ConnectionStatus, SignalLoss + commands, Crosshair Tweaks and Paperdoll (hud-overlay group). */
public final class OverlayFeatures1201 {
    private static boolean drawnThisFrame;

    private OverlayFeatures1201() {}

    public static void register() {
        Crosshair1201.register();
        PaperDoll1201.register();
        TabTweaks1201.register();
        SignalLoss1201.register();
    }

    /**
     * Gui.render TAIL (OverlayGuiMixin). On plain Fabric this draws the group's HUD inside the HUD pass. Zero SMP's Forge replaces
     * Gui.render with ForgeGui.render (an override that never calls it), so there {@link #afterHud} draws it instead.
     */
    public static void hud(GuiGraphics graphics) {
        drawnThisFrame = true;
        SignalLoss1201.render(graphics);
    }

    /** GameRenderer.render right after the HUD pass, before overlays and screens (OverlayGameRendererMixin); every frame. */
    public static void afterHud(boolean renderedWorld) {
        boolean drawn = drawnThisFrame;
        drawnThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        if (!renderedWorld || mc.level == null || mc.player == null) return;
        boolean hudFallback = !drawn && !mc.options.hideGui;
        boolean hiddenCrosshair = mc.options.hideGui && Crosshair1201.active();
        if (!hudFallback && !hiddenCrosshair) return;
        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        if (hiddenCrosshair) Crosshair1201.renderHiddenHud(graphics);
        if (hudFallback) SignalLoss1201.render(graphics);
        graphics.flush();
    }
}
