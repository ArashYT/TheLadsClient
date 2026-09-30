package com.thelads.core.v1_21_1.mixin.chrome;

import com.thelads.core.v1_21_1.gui.TitleWidgetRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads title/More widgets draw through TitleScreenTheme; scroll areas advance their smooth scroll once per frame (26.x). */
@Mixin(AbstractWidget.class)
public abstract class TitleWidgetMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), require = 1)
    private void ladsAdvanceScroll(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof com.thelads.core.v1_21_1.gui.SmoothScrollTarget target) target.ladsAdvanceScroll();
    }

    @Shadow protected abstract void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    // 1.21.1 widgets have no cursor handling to keep.
    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
        require = 1)
    private void ladsRenderRegisteredTitleWidget(AbstractWidget widget, GuiGraphics graphics,
        int mouseX, int mouseY, float partialTick) {
        if (!TitleWidgetRegistry.render(widget)) renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
