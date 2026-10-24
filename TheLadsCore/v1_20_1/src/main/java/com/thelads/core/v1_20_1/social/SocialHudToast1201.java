package com.thelads.core.v1_20_1.social;

import com.mojang.blaze3d.platform.Window;
import com.thelads.core.client.social.ui.SocialToasts;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the common social toasts on 1.20.1's HUD layer (top right) and routes clicks on them (MouseHandlerMixin). */
public final class SocialHudToast1201 {
    private SocialHudToast1201() {}

    public static void render(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (SocialToasts.isEmpty() || mc.options.hideGui) return;
        Window w = mc.getWindow();
        int mx = (int) (mc.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight());
        SocialToasts.render(new GuiGraphicsLadsAdapter1201(g, mc.font), w.getGuiScaledWidth(), w.getGuiScaledHeight(), mx, my);
    }

    public static boolean onClick(double mx, double my) {
        Window w = Minecraft.getInstance().getWindow();
        return SocialToasts.mouseClicked(mx, my, 0, w.getGuiScaledWidth(), w.getGuiScaledHeight());
    }
}
