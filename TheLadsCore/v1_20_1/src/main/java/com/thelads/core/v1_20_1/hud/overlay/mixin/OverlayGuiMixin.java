package com.thelads.core.v1_20_1.hud.overlay.mixin;

import com.thelads.core.v1_20_1.hud.overlay.Crosshair1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crosshair Tweaks replaces vanilla's crosshair (also called by Forge's CROSSHAIR overlay). The Action Bar module takes the action bar
 * where it is set (Forge's RECORD_OVERLAY then has none to draw).
 */
@Mixin(Gui.class)
public class OverlayGuiMixin {
    @Shadow private int overlayMessageTime;

    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void lads$crosshair(GuiGraphics graphics, CallbackInfo callback) {
        if (!Crosshair1201.active()) return;
        Crosshair1201.renderInHud(graphics);
        callback.cancel();
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void lads$actionBar(Component message, boolean animate, CallbackInfo callback) {
        if (!com.thelads.core.client.hud.ActionBar.set(message, animate)) return;
        overlayMessageTime = 0;
        callback.cancel();
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void lads$actionBarTick(CallbackInfo callback) {
        com.thelads.core.client.hud.ActionBar.tick();
    }
}
