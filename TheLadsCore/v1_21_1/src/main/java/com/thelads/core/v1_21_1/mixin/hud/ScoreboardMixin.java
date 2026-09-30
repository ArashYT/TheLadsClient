package com.thelads.core.v1_21_1.mixin.hud;

import com.thelads.core.client.hud.ScoreboardHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Lads Scoreboard widget replaces vanilla's sidebar; Edit HUD hides it too, as on 26.x. */
@Mixin(Gui.class)
public class ScoreboardMixin {
    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V", at = @At("HEAD"), cancellable = true)
    private void ladsSidebar(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof com.thelads.core.v1_21_1.gui.DraggableHudScreen121
            || ScoreboardHudElement.shouldReplaceVanillaScoreboard()) ci.cancel();
    }
}
