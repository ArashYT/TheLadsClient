package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.client.hud.ScoreboardHudElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class ScoreboardMixin {
    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSidebar(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
        // As on 26.x, Edit HUD also hides vanilla's sidebar so only the movable Lads preview shows.
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof com.thelads.core.v1_21_11.gui.DraggableHudScreen12111
            || ScoreboardHudElement.shouldReplaceVanillaScoreboard()) ci.cancel();
    }
}
