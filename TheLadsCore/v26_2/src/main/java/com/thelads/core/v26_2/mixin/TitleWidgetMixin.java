package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v26_2.gui.TitleWidgetRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractWidget.class)
public abstract class TitleWidgetMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method="extractRenderState",at=@At("HEAD"),require=1)
    private void ladsAdvanceScroll(GuiGraphicsExtractor g,int mx,int my,float dt,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if((Object)this instanceof com.thelads.core.v26_2.gui.SmoothScrollTarget target)target.ladsAdvanceScroll();
    }

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
        } else if (ButtonLift.enabled(widget)) {
            // The pause menu's buttons grow a little about their centre while hovered, label included.
            ButtonLift.update(widget, widget.active && widget.isHoveredOrFocused());
            float scale = 1 + TitleScreenTheme.LIFT * ButtonLift.eased(widget);
            float cx = widget.getX() + widget.getWidth() / 2f, cy = widget.getY() + widget.getHeight() / 2f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-cx, -cy);
            try {
                extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            } finally {
                graphics.pose().popMatrix();
            }
        } else {
            extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }
}
