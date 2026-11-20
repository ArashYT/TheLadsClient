package com.thelads.core.v1_21_11.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Where server dialogs are drawn on 1.21.11: the in-game HUD (DialogHudMixin), every screen (Fabric event), and the modal screen. */
public final class DialogUi12111 {
    private DialogUi12111() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DialogScreen12111 || screen instanceof net.minecraft.client.gui.screens.PauseScreen) return; // the pause menu is never covered
            ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) ->
                DialogOverlay.render(new GuiGraphicsLadsAdapter(graphics, client.font), mouseX, mouseY));
        });
    }

    /** HUD pass, no screen: the mouse is grabbed, so nothing is hovered. */
    public static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !DialogOverlay.any()) return;
        DialogOverlay.render(new GuiGraphicsLadsAdapter(graphics, mc.font), -1, -1);
    }

    /** Client tick: a modal takes the mouse by opening its screen (only over the bare game, never over another screen). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && DialogOverlay.modal() != null) mc.setScreen(new DialogScreen12111());
    }
}
