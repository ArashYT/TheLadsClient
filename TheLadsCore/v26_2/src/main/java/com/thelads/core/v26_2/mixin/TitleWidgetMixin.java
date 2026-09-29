package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.gui.TitleWidgetRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractWidget.class)
public abstract class TitleWidgetMixin {
    @Shadow protected abstract void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);
    @Shadow protected abstract void handleCursor(GuiGraphicsExtractor graphics);

    @Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractWidget;extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
        require = 1)
    private void ladsRenderRegisteredTitleWidget(AbstractWidget widget, GuiGraphicsExtractor graphics,
        int mouseX, int mouseY, float partialTick) {
        if (TitleWidgetRegistry.render(widget)) {
            handleCursor(graphics);
        } else {
            extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }
}
