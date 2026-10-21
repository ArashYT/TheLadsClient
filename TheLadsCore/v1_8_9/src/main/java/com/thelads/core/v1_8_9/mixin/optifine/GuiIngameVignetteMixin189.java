package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine Vignette setting: controls edge shading vignette.
 * 0=Default, 1=Fast (vignette disabled), 2=Fancy (vignette enabled).
 */
@Mixin(GuiIngame.class)
public abstract class GuiIngameVignetteMixin189 {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void lads$renderVignette(float lightLevel, ScaledResolution scaledRes, CallbackInfo ci) {
        if (OptiFine189Module.sOfVignette == 1) { // Fast (OFF)
            ci.cancel();
        }
    }
}
