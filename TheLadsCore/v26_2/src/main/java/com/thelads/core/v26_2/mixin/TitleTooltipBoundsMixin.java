package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.title.TitleScreenTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While the title flushes its tooltips inside the canvas pose (TitleScreenTheme.flushTooltips), the screen they must stay on is the canvas. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TitleTooltipBoundsMixin {
    @Inject(method = "guiWidth()I", at = @At("HEAD"), cancellable = true)
    private void ladsCanvasWidth(CallbackInfoReturnable<Integer> cir) {
        if (TitleScreenTheme.tooltipWidth() > 0) cir.setReturnValue(TitleScreenTheme.tooltipWidth());
    }

    @Inject(method = "guiHeight()I", at = @At("HEAD"), cancellable = true)
    private void ladsCanvasHeight(CallbackInfoReturnable<Integer> cir) {
        if (TitleScreenTheme.tooltipHeight() > 0) cir.setReturnValue(TitleScreenTheme.tooltipHeight());
    }
}
