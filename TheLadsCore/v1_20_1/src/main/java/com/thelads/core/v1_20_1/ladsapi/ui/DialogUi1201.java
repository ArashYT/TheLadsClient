package com.thelads.core.v1_20_1.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Where server dialogs are drawn on 1.20.1: the in-game HUD (DialogHudMixin), every screen (Fabric event), and the modal screen. */
public final class DialogUi1201 {
    private DialogUi1201() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DialogScreen1201 || screen instanceof net.minecraft.client.gui.screens.PauseScreen) return; // the pause menu is never covered
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) ->
                DialogOverlay.render(new GuiGraphicsLadsAdapter1201(graphics, client.font), mouseX, mouseY));
        });
    }

    /** HUD pass, no screen: the mouse is grabbed, so nothing is hovered. */
    public static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !DialogOverlay.any()) return;
        DialogOverlay.render(new GuiGraphicsLadsAdapter1201(graphics, mc.font), -1, -1);
    }

    /** Client tick: a modal takes the mouse by opening its screen (only over the bare game, never over another screen). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && DialogOverlay.modal() != null) mc.setScreen(new DialogScreen1201());
    }
}
