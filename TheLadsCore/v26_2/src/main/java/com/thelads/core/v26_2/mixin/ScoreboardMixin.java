package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.hud.PotionHudElement;
import com.thelads.core.client.hud.ScoreboardHudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class ScoreboardMixin {
    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.thelads.core.v26_2.gui.DraggableHudScreen26 || ScoreboardHudElement.shouldReplaceVanillaScoreboard()) ci.cancel();
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$effects(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (PotionHudElement.shouldReplaceVanillaEffects()) ci.cancel();
    }
}
