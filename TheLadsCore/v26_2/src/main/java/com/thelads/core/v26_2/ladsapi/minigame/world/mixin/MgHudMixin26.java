package com.thelads.core.v26_2.ladsapi.minigame.world.mixin;

import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v26_2.ladsapi.minigame.world.WorldOverlay26;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minigame screen overlays (fx, bars, waypoints) over the HUD; a camera shot with hide_hud drops the whole HUD and keeps only fx and bars. */
@Mixin(Hud.class)
public abstract class MgHudMixin26 {
    private static final String EXTRACT = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";

    @Inject(method = EXTRACT, at = @At("HEAD"), cancellable = true)
    private void lads$hideHud(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (!Minigame.camera.hidesHud(Minigame.now())) return;
        WorldOverlay26.draw(graphics, false);
        ci.cancel();
    }

    @Inject(method = EXTRACT, at = @At("TAIL"))
    private void lads$overlays(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        WorldOverlay26.draw(graphics, true);
    }
}
