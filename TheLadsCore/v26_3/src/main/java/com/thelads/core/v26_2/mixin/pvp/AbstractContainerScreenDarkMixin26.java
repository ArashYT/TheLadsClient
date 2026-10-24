package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dark Mode: the container's background (texture and, in the inventory, your player) is drawn by extractBackground before
 * extractContents; a dark fill goes over it first thing there, under the widgets, slots, items and labels. While the labels draw,
 * GuiGraphicsLabelMixin26 turns their dark gray light.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenDarkMixin26 {
    private static final String LABELS = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V";
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow @Final protected int imageWidth;
    @Shadow @Final protected int imageHeight;

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void lads$darken(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (PvpVisuals.darkFill != 0) graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PvpVisuals.darkFill);
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = LABELS))
    private void lads$labelsStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        PvpVisuals.containerLabels = true;
    }

    @Inject(method = "extractContents", at = @At(value = "INVOKE", target = LABELS, shift = At.Shift.AFTER))
    private void lads$labelsEnd(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        PvpVisuals.containerLabels = false;
    }
}
