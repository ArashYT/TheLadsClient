package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.gui.TitleWidgetRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractWidget.class)
public abstract class TitleWidgetMixin {
    @Shadow protected abstract void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    @Shadow protected abstract void handleCursor(GuiGraphics graphics);

    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
        require = 1)
    private void ladsRenderRegisteredTitleWidget(AbstractWidget widget, GuiGraphics graphics,
        int mouseX, int mouseY, float partialTick) {
        if (TitleWidgetRegistry.render(widget)) {
            handleCursor(graphics);
        } else {
            renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }
}
