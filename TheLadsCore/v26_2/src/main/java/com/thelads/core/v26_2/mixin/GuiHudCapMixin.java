package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.HudCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The HUD FPS cap wraps the whole HUD build, including every injection other mods make into it, and its deferred end (HudCapture). */
@Mixin(Gui.class)
public class GuiHudCapMixin {
    @Shadow @Final private GuiRenderState guiRenderState;

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"),
        require = 1)
    private void lads$capHud(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker delta, Operation<Void> original) {
        HudCapture.extract(guiRenderState, () -> original.call(hud, graphics, delta));
    }

    /** The HUD's deferred end (subtitles and Fabric's last HUD layer: Jade, Item Physics' throw bar) goes with the HUD it belongs to. */
    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractDeferredSubtitles()V"), require = 1)
    private void lads$capDeferred(Hud hud, Operation<Void> original) {
        HudCapture.extractDeferred(guiRenderState, () -> original.call(hud));
    }
}
