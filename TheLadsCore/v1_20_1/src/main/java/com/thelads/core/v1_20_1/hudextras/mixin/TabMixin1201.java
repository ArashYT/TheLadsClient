package com.thelads.core.v1_20_1.hudextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.client.hud.HudExtras;
import com.thelads.core.modules.TabListModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tab list extras (OneClient's VanillaHUD): your own row first, and the list opening and closing animated (scaled from its top). */
@Mixin(PlayerTabOverlay.class)
public class TabMixin1201 {
    @ModifyReturnValue(method = "getPlayerInfos", at = @At("RETURN"))
    private List<PlayerInfo> lads$selfFirst(List<PlayerInfo> rows) {
        var player = Minecraft.getInstance().player;
        if (!HudExtras.TAB.selfFirst || player == null) return rows;
        for (int i = 1; i < rows.size(); i++) {
            if (!player.getUUID().equals(rows.get(i).getProfile().getId())) continue;
            List<PlayerInfo> moved = new ArrayList<>(rows); // vanilla's list is unmodifiable
            moved.add(0, moved.remove(i));
            return moved;
        }
        return rows;
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V")
    private void lads$animate(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, Operation<Void> original) {
        TabListModule tab = HudExtras.TAB;
        float open = tab.animated ? tab.openness() : 1;
        if (open >= 1) {
            original.call(graphics, width, scoreboard, objective);
            return;
        }
        float cx = graphics.guiWidth() / 2f;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(cx, 10, 0);
            graphics.pose().scale(open, open, 1);
            graphics.pose().translate(-cx, -10, 0);
            original.call(graphics, width, scoreboard, objective);
        } finally {
            graphics.pose().popPose();
        }
    }
}
