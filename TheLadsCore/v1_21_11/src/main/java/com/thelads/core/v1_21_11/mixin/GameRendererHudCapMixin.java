package com.thelads.core.v1_21_11.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_21_11.feature.HudCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The HUD FPS cap wraps the whole HUD build, including every injection other mods make into it (HudCapture). */
@Mixin(GameRenderer.class)
public class GameRendererHudCapMixin {
    @Shadow @Final GuiRenderState guiRenderState;

    @WrapOperation(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void lads$capHud(Gui gui, GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        HudCapture.extract(guiRenderState, () -> original.call(gui, graphics, delta));
    }
}
