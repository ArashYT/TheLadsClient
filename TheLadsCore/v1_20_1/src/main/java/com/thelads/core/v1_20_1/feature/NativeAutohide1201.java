package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.hud.HudManager;
import com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201;
import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class NativeAutohide1201 {
    private NativeAutohide1201() {}

    public static void renderLadsHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DraggableHudScreen1201) return;
        HudManager.getInstance().render(new GuiGraphicsLadsAdapter1201(graphics, mc.font));
    }
}
