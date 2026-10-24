package com.thelads.core.v1_20_1.social;

import com.mojang.blaze3d.platform.Window;
import com.thelads.core.client.social.ui.SocialToasts;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The common social toasts on 1.20.1, top right: on the HUD and over every screen (menus too); clicks: MouseHandlerMixin. */
public final class SocialHudToast1201 {
    private SocialHudToast1201() {}

    public static void register() {
        HudRenderCallback.EVENT.register((g, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && !mc.options.hideGui) draw(g, -1, -1); // with a screen open: drawn over it instead
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> draw(g, mx, my)));
    }

    private static void draw(GuiGraphics g, int mx, int my) {
        if (SocialToasts.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        SocialToasts.render(new GuiGraphicsLadsAdapter1201(g, mc.font), w.getGuiScaledWidth(), w.getGuiScaledHeight(), mx, my);
    }

    public static boolean onClick(double mx, double my) {
        Window w = Minecraft.getInstance().getWindow();
        return SocialToasts.mouseClicked(mx, my, 0, w.getGuiScaledWidth(), w.getGuiScaledHeight());
    }
}
