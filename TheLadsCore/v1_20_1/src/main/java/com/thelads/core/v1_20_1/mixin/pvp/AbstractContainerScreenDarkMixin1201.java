package com.thelads.core.v1_20_1.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dark Mode: a dark fill over the container's background (texture and, in the inventory, your player) before its slots, items and
 * labels draw; while the labels draw, GuiGraphicsLabelMixin1201 turns their dark gray light.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenDarkMixin1201 {
    private static final String LABELS = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V";
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void lads$darken(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (PvpVisuals.darkFill != 0) graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PvpVisuals.darkFill);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = LABELS))
    private void lads$labelsStart(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PvpVisuals.containerLabels = true;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = LABELS, shift = At.Shift.AFTER))
    private void lads$labelsEnd(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PvpVisuals.containerLabels = false;
    }
}
