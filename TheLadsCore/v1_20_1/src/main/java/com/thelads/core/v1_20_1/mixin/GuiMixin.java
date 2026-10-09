package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.NativeAutohide1201;
import com.thelads.core.v1_20_1.feature.NativeKillBanner1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), require = 1)
    private void onRender(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        NativeAutohide1201.renderLadsHud(guiGraphics);
        NativeKillBanner1201.render(guiGraphics);
    }
}
