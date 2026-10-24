package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.PvpVisuals;
import com.thelads.core.config.ModuleSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PvP visuals on 1.8.9 (common PvpVisuals): module statuses and the name tag's ping and health. The render hooks are the mixins in
 * mixin.pvp and the Nametag mixins; TheLadsCore189's tick refreshes PvpVisuals.
 */
public final class PvpVisuals189 {
    private static final String HEART = "\u2764", MS = "ms";

    private PvpVisuals189() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(PvpVisuals.RENDER_TWEAKS, PvpVisuals.OVERLAYS, PvpVisuals.DARK_MODE, PvpVisuals.NAMETAG_HEALTH);
        ModuleSupport.registerUnavailable(PvpVisuals.TOTEM, "Minecraft 1.8.9 has no Totem of Undying; it was added in Minecraft 1.11.");
    }

    /** Nametags' Show Ping and Nametag Health, after a player's name (Render.renderLivingLabel, after its normal-pass text). */
    public static void tagExtras(FontRenderer font, Entity entity, String name) {
        if (!(entity instanceof EntityPlayer) || !PvpVisuals.nametagPing && !PvpVisuals.nametagHealth) return;
        int x = font.getStringWidth(name) / 2 + 3;
        if (PvpVisuals.nametagPing) {
            NetHandlerPlayClient net = Minecraft.getMinecraft().getNetHandler();
            NetworkPlayerInfo info = net == null ? null : net.getPlayerInfo(entity.getUniqueID());
            if (info != null) {
                int color = PvpVisuals.alpha(PvpVisuals.pingColor(info.getResponseTime()), PvpVisuals.tagAlpha);
                x = font.drawString(PvpVisuals.number(info.getResponseTime()), x, 0, color);
                x = font.drawString(MS, x, 0, color) + 3;
            }
        }
        if (PvpVisuals.nametagHealth) {
            int color = PvpVisuals.alpha(0xFFFF5555, PvpVisuals.tagAlpha);
            x = font.drawString(PvpVisuals.number((int) Math.ceil(((EntityPlayer) entity).getHealth())), x, 0, color);
            font.drawString(HEART, x, 0, color);
        }
    }
}
