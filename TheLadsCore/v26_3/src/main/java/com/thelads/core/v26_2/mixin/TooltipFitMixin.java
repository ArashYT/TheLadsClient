package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.TooltipFit;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tooltips too big for the screen are drawn smaller (TooltipFit): every tooltip goes through tooltip(), which extracts it again in
 * a space larger by the scale, laid out by its own positioner there.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipFitMixin {
    @Shadow @Final private Matrix3x2fStack pose;
    @Unique private boolean lads$fitting;

    @Shadow public abstract int guiWidth();
    @Shadow public abstract int guiHeight();
    @Shadow public abstract void tooltip(Font font, List<ClientTooltipComponent> lines, int xo, int yo, ClientTooltipPositioner positioner,
                                         Identifier style, boolean extraSpaceAfterFirstLine);

    @Inject(method = "tooltip", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$fit(Font font, List<ClientTooltipComponent> lines, int xo, int yo, ClientTooltipPositioner positioner,
                          Identifier style, boolean extraSpaceAfterFirstLine, CallbackInfo ci) {
        if (lads$fitting || lines.isEmpty()) return;
        int width = 0, height = extraSpaceAfterFirstLine && lines.size() != 1 ? 0 : -2; // measured as vanilla does
        for (ClientTooltipComponent line : lines) {
            width = Math.max(width, line.getWidth(font));
            height += line.getHeight(font);
        }
        float scale = TooltipFit.scale(width, height, guiWidth(), guiHeight(), Minecraft.getInstance().getWindow().getGuiScale());
        if (scale >= 1F) return;
        ci.cancel();
        pose.pushMatrix();
        pose.scale(scale, scale);
        lads$fitting = true;
        try {
            tooltip(font, lines, (int) (xo / scale), (int) (yo / scale), (screenWidth, screenHeight, x, y, w, h) ->
                positioner.positionTooltip((int) (screenWidth / scale), (int) (screenHeight / scale), x, y, w, h), style, extraSpaceAfterFirstLine);
        } finally {
            lads$fitting = false;
            pose.popMatrix();
        }
    }
}
