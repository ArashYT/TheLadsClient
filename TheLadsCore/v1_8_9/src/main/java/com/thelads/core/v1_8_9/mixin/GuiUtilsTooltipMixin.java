package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Tooltips189;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiUtils.class, remap = false)
public abstract class GuiUtilsTooltipMixin {
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
            Tooltips189.drawBatchedTooltipBox(zLevel, left + 3, top + 3, right - left - 6, bottom - top - 6);
        }
    }
}
