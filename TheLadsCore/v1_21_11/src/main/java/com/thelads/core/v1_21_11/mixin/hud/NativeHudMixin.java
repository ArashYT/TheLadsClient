package com.thelads.core.v1_21_11.mixin.hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.thelads.core.client.FrameAnimation;
import com.thelads.core.v1_21_11.feature.NativeAutohide;
import com.thelads.core.v1_21_11.feature.NativeQualityOfLife;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SmoothHotbar and the Autohide scope of the hotbar, status bars and XP (26.x NativeHudMixin). */
@Mixin(Gui.class)
public class NativeHudMixin {
    @Unique private final FrameAnimation lads$selection = new FrameAnimation();

    /** The second sprite of renderItemHotbar is the selected-slot highlight; a wrap keeps other mods' changes to that call. */
    @WrapOperation(method = "renderItemHotbar", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void lads$selection(GuiGraphics graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
        double speed = switch (NativeQualityOfLife.choice("SmoothHotbar", "Speed", 1)) { case 0 -> 10; case 2 -> 28; default -> 18; };
        float offset = (float) (lads$selection.update(x, speed, System.nanoTime(), NativeQualityOfLife.enabled("SmoothHotbar")) - x);
        graphics.pose().pushMatrix();
        graphics.pose().translate(offset, 0);
        original.call(graphics, pipeline, sprite, x, y, width, height);
        graphics.pose().popMatrix();
    }
    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"))
    private void lads$fade(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) { NativeAutohide.scopeOpacity = NativeAutohide.update(); }
    @Inject(method = "renderHotbarAndDecorations", at = @At("RETURN"))
    private void lads$restore(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) { NativeAutohide.scopeOpacity = 1; }
}
