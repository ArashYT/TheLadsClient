package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.hud.ScoreboardHudElement;
import com.thelads.core.v1_20_1.gui.DraggableHudScreen1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
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
        if (Minecraft.getInstance().screen instanceof DraggableHudScreen1201
            || ScoreboardHudElement.shouldReplaceVanillaScoreboard()) {
            ci.cancel();
        }
    }
}
