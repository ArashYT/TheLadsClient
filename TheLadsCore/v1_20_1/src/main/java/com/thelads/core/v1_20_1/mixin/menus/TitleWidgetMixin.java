package com.thelads.core.v1_20_1.mixin.menus;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.client.title.ButtonLift;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_20_1.gui.TitleWidgetRegistry1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The title/More screens draw their registered native widgets with the Lads button art, and the pause menu's buttons
 * grow a little about their centre while hovered (ButtonLift); everything else is drawn by its own widget code.
 */
@Mixin(AbstractWidget.class)
public abstract class TitleWidgetMixin {
    @WrapOperation(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
        require = 1)
    private void ladsRenderRegisteredTitleWidget(AbstractWidget widget, GuiGraphics graphics,
        int mouseX, int mouseY, float partialTick, Operation<Void> original) {
        if (TitleWidgetRegistry1201.render(widget)) {
            return;
        }
        if (ButtonLift.enabled(widget)) {
            // The pause menu's buttons grow a little about their centre while hovered, label included.
            ButtonLift.update(widget, widget.active && widget.isHoveredOrFocused());
            float scale = 1 + TitleScreenTheme.LIFT * ButtonLift.eased(widget);
            float cx = widget.getX() + widget.getWidth() / 2f, cy = widget.getY() + widget.getHeight() / 2f;
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.pose().translate(-cx, -cy, 0);
            try {
                original.call(widget, graphics, mouseX, mouseY, partialTick);
            } finally {
                graphics.pose().popPose();
            }
        } else {
            original.call(widget, graphics, mouseX, mouseY, partialTick);
        }
    }
}
