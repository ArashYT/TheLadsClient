package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.YungsMenuTweaksManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to CycleButton to reverse cycle on right-click (YUNG's Menu Tweaks).
 */
@Environment(EnvType.CLIENT)
@Mixin(CycleButton.class)
public abstract class MixinCycleButton<T> extends AbstractButton {

    public MixinCycleButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onCycleButtonClicked(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (this.active && this.visible && this.isMouseOver(event.x(), event.y())) {
            if (event.button() == 1 && YungsMenuTweaksManager.isRightClickReversesCycleButtonsEnabled()) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.mouseScrolled(event.x(), event.y(), 0.0, 1.0);
                cir.setReturnValue(true);
            }
        }
    }
}
