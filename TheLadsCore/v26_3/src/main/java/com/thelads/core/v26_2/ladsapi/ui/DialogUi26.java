package com.thelads.core.v26_2.ladsapi.ui;

import com.thelads.core.client.hud.DialogOverlay;
import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Where server dialogs are drawn on 26.3: the in-game HUD (DialogHudMixin), every screen (Fabric event), and the modal screen. */
public final class DialogUi26 {
    private DialogUi26() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof DialogScreen26 || screen instanceof net.minecraft.client.gui.screens.PauseScreen) return; // the pause menu is never covered
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
                DialogOverlay.render(new GuiGraphicsExtractorLadsAdapter(graphics, client.font), mouseX, mouseY));
        });
    }

    /** HUD pass, no screen: the mouse is grabbed, so nothing is hovered. */
    public static void renderHud(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || !DialogOverlay.any()) return;
        DialogOverlay.render(new GuiGraphicsExtractorLadsAdapter(graphics, mc.font), -1, -1);
    }

    /** Client tick: a modal takes the mouse by opening its screen (only over the bare game, never over another screen). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gui.screen() == null && DialogOverlay.modal() != null) mc.setScreenAndShow(new DialogScreen26());
    }
}
