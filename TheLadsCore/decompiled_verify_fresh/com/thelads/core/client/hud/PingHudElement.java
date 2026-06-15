/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.PlayerInfo
 */
package com.thelads.core.client.hud;

import com.thelads.core.client.hud.HudElement;
import com.thelads.core.config.HudSettings;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingHudElement
extends HudElement {
    public PingHudElement() {
        this.x = 5;
        this.y = 65;
        this.width = 60;
        this.height = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g) {
        PlayerInfo info;
        this.drawBackground(g);
        Minecraft mc = Minecraft.getInstance();
        int ping = 0;
        if (mc.player != null && mc.getConnection() != null && (info = mc.getConnection().getPlayerInfo(mc.player.getUUID())) != null) {
            ping = info.getLatency();
        }
        boolean showLabel = this.optBool("Show label", true);
        boolean colorByPing = this.optBool("Color by ping", true);
        Object text = showLabel ? ping + " ms" : String.valueOf(ping);
        int color = this.resolveColor();
        if (colorByPing) {
            color = ping < 80 ? -11141291 : (ping < 150 ? -171 : (ping < 300 ? -22016 : -43691));
        }
        Minecraft mcc = Minecraft.getInstance();
        int tw = mcc.font.width((String)text);
        int tx = this.x + (this.width - tw) / 2;
        Objects.requireNonNull(mcc.font);
        int ty = this.y + (this.height - 9) / 2 + 1;
        g.text(mcc.font, (String)text, tx, ty, color, HudSettings.getInstance().isTextShadow());
    }
}

