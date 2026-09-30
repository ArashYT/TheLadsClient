package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.thelads.core.v26_2.feature.FrameAnimation;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class NativeHudMixin {
    @Unique private final FrameAnimation lads$selection = new FrameAnimation();

    @Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"), require = 1)
    private void lads$selection(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                               int x, int y, int width, int height) {
        double speed = switch (NativeQualityOfLife.choice("SmoothHotbar", "Speed", 1)) {
            case 0 -> 10;
            case 2 -> 28;
            default -> 18;
        };
        float offset = (float) (lads$selection.update(x, speed, System.nanoTime(),
            NativeQualityOfLife.enabled("SmoothHotbar")) - x);
        graphics.pose().pushMatrix();
        graphics.pose().translate(offset, 0);
        com.thelads.core.v26_2.feature.raised.NativeRaised.selection(graphics, pipeline, sprite, x, y, width, height);
        graphics.pose().popMatrix();
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), require = 1)
    private void lads$raise(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        com.thelads.core.v26_2.feature.NativeAutohide.scopeOpacity=com.thelads.core.v26_2.feature.NativeAutohide.update();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, -NativeQualityOfLife.raisedDistance());
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"), require = 1)
    private void lads$restore(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        com.thelads.core.v26_2.feature.NativeAutohide.scopeOpacity=1;
        graphics.pose().popMatrix();
    }
}
