package com.thelads.core.v1_20_1.ladsapi.minigame.world.mixin;

import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_20_1.ladsapi.minigame.world.WorldOverlay1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minigame screen overlays (fx, bars, waypoints) over the HUD; a camera shot with hide_hud drops the whole HUD and keeps only fx and bars. */
@Mixin(value = Gui.class, priority = 1100)
public abstract class MgHudMixin1201 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void lads$hideHud(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (!Minigame.camera.hidesHud(Minigame.now())) return;
        WorldOverlay1201.draw(graphics, false);
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void lads$overlays(GuiGraphics graphics, float partial, CallbackInfo ci) {
        WorldOverlay1201.draw(graphics, true);
    }
}
