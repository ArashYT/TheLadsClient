package com.thelads.core.v1_20_1.hud.items.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.TooltipFit;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tooltips too big for the screen are drawn smaller (TooltipFit): every tooltip (items, Forge's, Lads' images) goes through
 * renderTooltipInternal, which draws it again in a space larger by the scale, laid out by its own positioner there.
 */
@Mixin(GuiGraphics.class)
abstract class TooltipFitMixin {
    @Shadow @Final private PoseStack pose;
    @Unique private boolean lads$fitting;

    @Shadow public abstract int guiWidth();
    @Shadow public abstract int guiHeight();
    @Shadow private void renderTooltipInternal(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner) {}

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void lads$fit(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, CallbackInfo ci) {
        if (lads$fitting || lines.isEmpty()) return;
        int width = 0, height = lines.size() == 1 ? -2 : 0; // measured as vanilla does
        for (ClientTooltipComponent line : lines) {
            width = Math.max(width, line.getWidth(font));
            height += line.getHeight();
        }
        float scale = TooltipFit.scale(width, height, guiWidth(), guiHeight(), Minecraft.getInstance().getWindow().getGuiScale());
        if (scale >= 1F) return;
        ci.cancel();
        pose.pushPose();
        pose.scale(scale, scale, 1F);
        lads$fitting = true;
        try {
            renderTooltipInternal(font, lines, (int) (x / scale), (int) (y / scale), (screenWidth, screenHeight, mouseX, mouseY, w, h) ->
                positioner.positionTooltip((int) (screenWidth / scale), (int) (screenHeight / scale), mouseX, mouseY, w, h));
        } finally {
            lads$fitting = false;
            pose.popPose();
        }
    }
}
