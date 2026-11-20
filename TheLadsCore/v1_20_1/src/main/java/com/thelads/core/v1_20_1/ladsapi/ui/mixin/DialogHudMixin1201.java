package com.thelads.core.v1_20_1.ladsapi.ui.mixin;

import com.thelads.core.v1_20_1.ladsapi.ui.DialogUi1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server dialogs on the in-game HUD (screens draw them through the Fabric screen event). */
@Mixin(value = Gui.class, priority = 1200)
public abstract class DialogHudMixin1201 {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"))
    private void lads$dialogs(GuiGraphics graphics, float partial, CallbackInfo ci) {
        DialogUi1201.renderHud(graphics);
    }
}
