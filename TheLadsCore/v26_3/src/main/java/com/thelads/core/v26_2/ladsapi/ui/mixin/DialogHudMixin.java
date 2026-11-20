package com.thelads.core.v26_2.ladsapi.ui.mixin;

import com.thelads.core.v26_2.ladsapi.ui.DialogUi26;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server dialogs on the in-game HUD (screens draw them through the Fabric screen event). */
@Mixin(Hud.class)
public abstract class DialogHudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL"))
    private void lads$dialogs(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        DialogUi26.renderHud(graphics);
    }
}
