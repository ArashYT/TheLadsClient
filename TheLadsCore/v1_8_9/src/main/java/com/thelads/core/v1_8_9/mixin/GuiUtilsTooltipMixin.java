package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Tooltips189;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiUtils.class, remap = false)
public abstract class GuiUtilsTooltipMixin {
    /** Scrollable Tooltips: the screen this tooltip is laid out in (TooltipFit's larger one while it draws smaller). */
    @Inject(method = "drawHoveringText", at = @At("HEAD"), remap = false)
    private static void ladsTooltipStart(List<String> lines, int mouseX, int mouseY, int screenWidth, int screenHeight, int maxTextWidth,
                                         FontRenderer font, CallbackInfo ci) {
        com.thelads.core.v1_8_9.qol.Qol189.tooltipStart(screenWidth, screenHeight);
    }

    @Inject(method = "drawHoveringText", at = @At("RETURN"), remap = false)
    private static void ladsTooltipEnd(List<String> lines, int mouseX, int mouseY, int screenWidth, int screenHeight, int maxTextWidth,
                                       FontRenderer font, CallbackInfo ci) {
        com.thelads.core.v1_8_9.qol.Qol189.tooltipEnd();
    }

    @Redirect(
        method = "drawHoveringText",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/fml/client/config/GuiUtils;drawGradientRect(IIIIIII)V"
        ),
        remap = false
    )
    private static void ladsBatchTooltipBox(int zLevel, int left, int top, int right, int bottom, int startColor, int endColor) {
        if (startColor == 0xF0100010 && bottom - top > 1 && right - left > 1) {
            // Scrollable Tooltips: everything after this (the box, then the text) is drawn moved by the scroll.
            com.thelads.core.v1_8_9.qol.Qol189.tooltipPlaced(left + 3, top + 3, right - left - 6, bottom - top - 6);
            Tooltips189.drawBatchedTooltipBox(zLevel, left + 3, top + 3, right - left - 6, bottom - top - 6);
        }
    }
}
