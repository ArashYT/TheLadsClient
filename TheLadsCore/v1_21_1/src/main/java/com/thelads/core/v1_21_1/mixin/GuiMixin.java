package com.thelads.core.v1_21_1.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void onRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // CPS counts press events in MouseHandlerMixin. Skips the editor's duplicate pass and fades every Lads widget with Autohide.
        com.thelads.core.v1_21_1.feature.NativeAutohide.renderLadsHud(guiGraphics);
    }
}
