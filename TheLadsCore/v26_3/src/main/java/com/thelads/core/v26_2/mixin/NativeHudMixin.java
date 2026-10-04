package com.thelads.core.v26_2.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.thelads.core.client.FrameAnimation;
import com.thelads.core.v26_2.feature.NativeQualityOfLife;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class NativeHudMixin {
    @Unique private final FrameAnimation lads$selection = new FrameAnimation();

    @Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"), require = 1)
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
        graphics.blitSprite(pipeline, sprite, x, y, width, height);
        com.thelads.core.v26_2.feature.Raised26.selectionBottom(graphics, x, y, width, height);
        graphics.pose().popMatrix();
    }

    // 1.7 Animations' No heart flashing: vanilla's health-bar blink flag.
    @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractHearts("
        + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V"), index = 10, require = 1)
    private boolean lads$noHeartFlash(boolean blink) {
        return com.thelads.core.v26_2.feature.NativeOldAnimations.heartsBlink(blink);
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), require = 1)
    private void lads$raise(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        com.thelads.core.v26_2.feature.NativeAutohide.scopeOpacity=com.thelads.core.v26_2.feature.NativeAutohide.update();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, -com.thelads.core.v26_2.feature.Raised26.hotbar());
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"), require = 1)
    private void lads$restore(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        com.thelads.core.v26_2.feature.NativeAutohide.scopeOpacity=1;
        graphics.pose().popMatrix();
    }

    // Raised: the action bar sits above the hotbar, so it moves with it.
    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), require = 1)
    private void lads$raiseOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, -com.thelads.core.v26_2.feature.Raised26.hotbar());
    }

    @Inject(method = "extractOverlayMessage", at = @At("RETURN"), require = 1)
    private void lads$restoreOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        graphics.pose().popMatrix();
    }
}
