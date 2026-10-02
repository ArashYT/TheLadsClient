package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.gui.TitleWidgetRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
public abstract class TitleWidgetMixin {
    /** Smooth scrolling: a scroll area eases toward its wheel target once per rendered frame (26.x). */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), require = 1)
    private void ladsAdvanceScroll(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof com.thelads.core.v1_21_11.gui.SmoothScrollTarget target) target.ladsAdvanceScroll();
    }

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
        } else if (com.thelads.core.client.title.ButtonLift.enabled(widget)) {
            // The pause menu's buttons grow a little about their centre while hovered, label included.
            com.thelads.core.client.title.ButtonLift.update(widget, widget.active && widget.isHoveredOrFocused());
            float scale = 1 + com.thelads.core.client.title.TitleScreenTheme.LIFT * com.thelads.core.client.title.ButtonLift.eased(widget);
            float cx = widget.getX() + widget.getWidth() / 2f, cy = widget.getY() + widget.getHeight() / 2f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-cx, -cy);
            try {
                renderWidget(graphics, mouseX, mouseY, partialTick);
            } finally {
                graphics.pose().popMatrix();
            }
        } else {
            renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }
}
