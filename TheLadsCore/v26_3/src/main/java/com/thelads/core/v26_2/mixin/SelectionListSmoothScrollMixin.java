package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.SmoothScrollTarget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Advances smooth scroll easing immediately before item rendering and scrollbar extraction
 * on every rendered frame for all AbstractSelectionList instances.
 */
@Mixin(AbstractSelectionList.class)
public abstract class SelectionListSmoothScrollMixin {
    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"), require = 1)
    private void ladsAdvanceOnSelectionListExtract(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                  float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof SmoothScrollTarget target) {
            target.ladsAdvanceScroll();
        }
    }
}
