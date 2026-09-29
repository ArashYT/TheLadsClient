// Adapted from TabTweaks 1.5.11 by MicrocontrollersDev, LGPL-3.0-only.
// Source faa19c704c3c967e1cf0f0355791f9d90d47a1c5; corresponding source in META-INF/lads-sources/tabtweaks.
package com.thelads.core.v26_2.feature.tabtweaks.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v26_2.feature.tabtweaks.Shifter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin implements Shifter {
    @Unique private int distance;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void clearPreviousBossBarHeight(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        distance = 0;
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;guiHeight()I", shift = At.Shift.AFTER))
    private void getBossBarHeight(GuiGraphicsExtractor graphics, CallbackInfo ci, @Local(name = "yOffset") int yOffset) {
        distance = yOffset - 19;
    }

    @Override
    public int ladsTab$getShift() {
        return distance;
    }
}
