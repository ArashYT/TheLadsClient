package com.thelads.core.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;

public class PingHudElement extends HudElement {
    public PingHudElement() {
        this.x = 5;
        this.y = 65;
        this.width = 60;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        int ping = 0;
        
        com.thelads.core.modules.PingViewModule pingMod = (com.thelads.core.modules.PingViewModule) com.thelads.core.config.ModuleManager.getInstance().getModule("PingView");
        if (pingMod != null && pingMod.getRealTimePing() >= 0) {
            ping = pingMod.getRealTimePing();
        } else if (mc.player != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ping = info.getLatency();
        }

        boolean showLabel = optBool("Show label", true);
        boolean colorByPing = optBool("Color by ping", true);

        String text = showLabel ? ping + " ms" : String.valueOf(ping);

        int color = resolveColor();
        if (colorByPing) {
            if (ping < 80)       color = 0xFF55FF55; // green
            else if (ping < 150) color = 0xFFFFFF55; // yellow
            else if (ping < 300) color = 0xFFFFAA00; // orange
            else                 color = 0xFFFF5555; // red
        }

        // draw with the (possibly ping-colored) color directly
        Minecraft mcc = Minecraft.getInstance();
        int tw = mcc.font.width(text);
        int tx = this.x + (this.width - tw) / 2;
        int ty = this.y + (this.height - mcc.font.lineHeight) / 2 + 1;
        g.text(mcc.font, text, tx, ty, color, com.thelads.core.config.HudSettings.getInstance().isTextShadow());
    }
}
