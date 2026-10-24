package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Overlays: the carved pumpkin and nether portal overlays at their opacity (GuiIngameForge calls both from its helmet and portal steps). */
@Mixin(GuiIngame.class)
public abstract class GuiIngameOverlayMixin189 {
    private static final String COLOR = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V";

    @Inject(method = "renderPumpkinOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsHidePumpkin(ScaledResolution res, CallbackInfo ci) {
        if (PvpVisuals.pumpkinOpacity <= 0) ci.cancel();
    }

    @ModifyArg(method = "renderPumpkinOverlay", at = @At(value = "INVOKE", target = COLOR, ordinal = 0), index = 3, require = 0)
    private float ladsPumpkinOpacity(float alpha) {
        return alpha * PvpVisuals.pumpkinOpacity;
    }

    @ModifyArg(method = "renderPortal(FLnet/minecraft/client/gui/ScaledResolution;)V", at = @At(value = "INVOKE", target = COLOR, ordinal = 0),
        index = 3, require = 0)
    private float ladsPortalOpacity(float alpha) {
        return alpha * PvpVisuals.portalOpacity;
    }
}
