package com.thelads.core.v1_21_11.ladsapi.minigame.world.mixin;

import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_21_11.ladsapi.minigame.world.WorldOverlay12111;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minigame screen overlays (fx, bars, waypoints) over the HUD; a camera shot with hide_hud drops the whole HUD and keeps only fx and bars. */
@Mixin(Gui.class)
public abstract class MgHudMixin12111 {
    private static final String EXTRACT = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V";

    @Inject(method = EXTRACT, at = @At("HEAD"), cancellable = true)
    private void lads$hideHud(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (!Minigame.camera.hidesHud(Minigame.now())) return;
        WorldOverlay12111.draw(graphics, false);
        ci.cancel();
    }

    @Inject(method = EXTRACT, at = @At("TAIL"))
    private void lads$overlays(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        WorldOverlay12111.draw(graphics, true);
    }
}
