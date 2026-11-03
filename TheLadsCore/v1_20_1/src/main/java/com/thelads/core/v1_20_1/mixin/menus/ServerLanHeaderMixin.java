package com.thelads.core.v1_20_1.mixin.menus;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Scanning for games" is centred on the screen on 1.20.1: with columns, on its own cell. */
@Mixin(ServerSelectionList.LANHeader.class)
abstract class ServerLanHeaderMixin {
    @Unique private int ladsCentre;

    @Inject(method = "render", at = @At("HEAD"))
    private void ladsCell(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        ladsCentre = left + width / 2;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;width:I"))
    private int ladsCellWidth(int screenWidth) {
        return ladsCentre * 2;
    }
}
