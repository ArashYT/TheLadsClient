package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Overlays: the carved pumpkin overlay (a worn item's camera overlay, the first texture overlay extractCameraOverlays draws; the
 * powder snow outline is the second) and the nether portal overlay at their opacity.
 */
@Mixin(Hud.class)
public class HudOverlayMixin26 {
    @ModifyArg(method = "extractCameraOverlays", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay("
        + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"), index = 2)
    private float lads$pumpkin(float alpha) {
        return alpha * PvpVisuals.pumpkinOpacity;
    }

    @ModifyArg(method = "extractPortalOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"), index = 0)
    private float lads$portal(float alpha) {
        return alpha * PvpVisuals.portalOpacity;
    }
}
