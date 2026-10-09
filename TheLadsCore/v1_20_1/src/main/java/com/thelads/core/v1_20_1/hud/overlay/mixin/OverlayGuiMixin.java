package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.thelads.core.v1_20_1.hud.overlay.Crosshair1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crosshair Tweaks replaces vanilla's crosshair (also called by Forge's CROSSHAIR overlay). */
@Mixin(Gui.class)
public class OverlayGuiMixin {
    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void lads$crosshair(GuiGraphics graphics, CallbackInfo callback) {
        if (!Crosshair1201.active()) return;
        Crosshair1201.renderInHud(graphics);
        callback.cancel();
    }
}
