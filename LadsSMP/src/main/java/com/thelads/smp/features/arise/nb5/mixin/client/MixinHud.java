package com.thelads.smp.features.arise.nb5.mixin.client;

import com.thelads.smp.features.arise.nb5.client.StaminaHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into Hud to render the stamina bar meter during gameplay.
 */
@Mixin(Hud.class)
public abstract class MixinHud {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL")
    )
    private void ladssmp$renderStaminaHud(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        StaminaHudRenderer.render(extractor, deltaTracker);
    }
}
