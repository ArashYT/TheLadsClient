package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeKillBanner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class KillBannerHudMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL"), require = 1)
    private void lads$killBanner(GuiGraphics graphics, DeltaTracker delta, CallbackInfo callback) {
        NativeKillBanner.render(graphics);
    }
}
