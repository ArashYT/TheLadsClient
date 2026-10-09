package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossBarMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ladsCustomBars(GuiGraphics g, CallbackInfo ci) {
        if (NativeFeatures1201.enabled("BossBar")) {
            ci.cancel();
        }
    }
}
