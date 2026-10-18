package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.YungsMenuTweaksManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to AbstractSliderButton to allow adjusting slider values using the mouse scroll wheel (YUNG's Menu Tweaks).
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractSliderButton.class)
public abstract class MixinAbstractSliderButton extends AbstractWidget {

    @Shadow
    protected double value;

    @Shadow
    protected abstract void setValue(double value);

    @Shadow
    protected abstract void applyValue();

    @Shadow
    protected abstract void updateMessage();

    public MixinAbstractSliderButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(
            method = "mouseScrolled(DDDD)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (this.active && this.visible && this.isMouseOver(mouseX, mouseY) && YungsMenuTweaksManager.isMouseScrollWheelAffectsSlidersEnabled()) {
            if (verticalAmount != 0.0) {
                double adjusted = YungsMenuTweaksManager.calculateAdjustedSliderValue(this.value, verticalAmount);
                this.setValue(adjusted);
                this.applyValue();
                this.updateMessage();
                cir.setReturnValue(true);
            }
        }
    }
}
