package com.thelads.core.v1_8_9.hudextras.mixin;

import com.thelads.core.client.hud.HudExtras;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Tab list Self First: your own row moved to the top of the sorted list (an ArrayList the method owns). */
@Mixin(GuiPlayerTabOverlay.class)
public class TabMixin189 {
    @ModifyVariable(method = "renderPlayerlist", at = @At("STORE"), ordinal = 0)
    private List<NetworkPlayerInfo> lads$selfFirst(List<NetworkPlayerInfo> rows) {
        net.minecraft.client.entity.EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (!HudExtras.TAB.selfFirst || player == null) return rows;
        for (int i = 1; i < rows.size(); i++) {
            if (!player.getUniqueID().equals(rows.get(i).getGameProfile().getId())) continue;
            rows.add(0, rows.remove(i));
            break;
        }
        return rows;
    }
}
