package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.HitmarkerManager;
import com.thelads.smp.features.arise.nb2.HoldMyBeerManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to Hud to render hitmarker ticks over crosshair (Hitmarker)
 * and empty hand mode status badge (Hold My Beer).
 */
@Environment(EnvType.CLIENT)
@Mixin(Hud.class)
public abstract class MixinHud {

    @Inject(
            method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL")
    )
    private void onExtractCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HitmarkerManager.renderCrosshairHitmarker(graphics);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL")
    )
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HoldMyBeerManager.renderHudIndicator(graphics);
    }
}
