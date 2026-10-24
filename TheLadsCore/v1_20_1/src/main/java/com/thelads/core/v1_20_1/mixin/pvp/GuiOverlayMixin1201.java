package com.thelads.core.v1_20_1.mixin.pvp;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.Gui;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Overlays: the carved pumpkin and nether portal overlays at their opacity (Forge's HUD overlays call the same two methods). */
@Mixin(Gui.class)
public class GuiOverlayMixin1201 {
    private static final String SET_COLOR = "Lnet/minecraft/client/gui/GuiGraphics;setColor(FFFF)V";
    @Shadow @Final private static ResourceLocation PUMPKIN_BLUR_LOCATION;

    @ModifyArg(method = "renderTextureOverlay", at = @At(value = "INVOKE", target = SET_COLOR, ordinal = 0), index = 3)
    private float lads$pumpkin(float alpha, @Local(argsOnly = true) ResourceLocation texture) {
        return texture == PUMPKIN_BLUR_LOCATION ? alpha * PvpVisuals.pumpkinOpacity : alpha;
    }

    @ModifyArg(method = "renderPortalOverlay", at = @At(value = "INVOKE", target = SET_COLOR, ordinal = 0), index = 3)
    private float lads$portal(float alpha) {
        return alpha * PvpVisuals.portalOpacity;
    }
}
