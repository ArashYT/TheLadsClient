package com.thelads.core.v1_21_11.ladsapi.ui.mixin;

import com.thelads.core.v1_21_11.ladsapi.ui.DialogUi12111;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server dialogs on the in-game HUD (screens draw them through the Fabric screen event). */
@Mixin(Gui.class)
public abstract class DialogHudMixin12111 {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL"))
    private void lads$dialogs(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        DialogUi12111.renderHud(graphics);
    }
}
