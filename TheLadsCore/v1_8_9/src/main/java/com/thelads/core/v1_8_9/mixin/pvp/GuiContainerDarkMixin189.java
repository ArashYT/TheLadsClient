package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dark Mode: a dark fill over the container's background (texture and, in the inventory, your player) before its slots, items and
 * labels draw; while the labels draw, FontRendererLabelMixin189 turns their dark gray light.
 */
@Mixin(GuiContainer.class)
public abstract class GuiContainerDarkMixin189 {
    private static final String BACKGROUND = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V";
    private static final String LABELS = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerForegroundLayer(II)V";
    @Shadow protected int guiLeft;
    @Shadow protected int guiTop;
    @Shadow protected int xSize;
    @Shadow protected int ySize;

    @Inject(method = "drawScreen", at = @At(value = "INVOKE", target = BACKGROUND, shift = At.Shift.AFTER))
    private void ladsDarken(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (PvpVisuals.darkFill == 0) return;
        Gui.drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, PvpVisuals.darkFill);
        GlStateManager.color(1, 1, 1, 1); // drawRect leaves its own color set
    }

    @Inject(method = "drawScreen", at = @At(value = "INVOKE", target = LABELS))
    private void ladsLabelsStart(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PvpVisuals.containerLabels = true;
    }

    @Inject(method = "drawScreen", at = @At(value = "INVOKE", target = LABELS, shift = At.Shift.AFTER))
    private void ladsLabelsEnd(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PvpVisuals.containerLabels = false;
    }
}
