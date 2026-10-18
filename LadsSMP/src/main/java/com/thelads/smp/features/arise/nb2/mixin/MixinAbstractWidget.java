package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.YungsMenuTweaksManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to AbstractWidget to support YUNG's Menu Tweaks:
 * - Intercepts mouseClicked to reverse cycle buttons on right-click.
 * - Introduces mouseScrolled to adjust option sliders using the mouse wheel.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractWidget.class)
public abstract class MixinAbstractWidget {

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onWidgetMouseClicked(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof CycleButton<?> cycleButton && YungsMenuTweaksManager.isRightClickReversesCycleButtonsEnabled()) {
            AbstractWidget widget = (AbstractWidget) (Object) this;
            if (widget.active && widget.visible && widget.isMouseOver(event.x(), event.y()) && event.button() == 1) {
                widget.playDownSound(Minecraft.getInstance().getSoundManager());
                cycleButton.mouseScrolled(event.x(), event.y(), 0.0, 1.0);
                cir.setReturnValue(true);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        if ((Object) this instanceof AbstractSliderButton slider && YungsMenuTweaksManager.isMouseScrollWheelAffectsSlidersEnabled()) {
            if (widget.active && widget.visible && widget.isMouseOver(mouseX, mouseY) && verticalAmount != 0.0) {
                AbstractSliderButtonAccessor accessor = (AbstractSliderButtonAccessor) slider;
                double adjusted = YungsMenuTweaksManager.calculateAdjustedSliderValue(accessor.getValue(), verticalAmount);
                accessor.invokeSetValue(adjusted);
                accessor.invokeApplyValue();
                accessor.invokeUpdateMessage();
                return true;
            }
        }
        return false;
    }
}
