package com.thelads.core.v1_20_1.pvp.mixin;

import com.thelads.core.v1_20_1.pvp.ScreenEffects1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu Blur: a menu's background starts (before vanilla's dim) with the world and HUD behind it blurred. Chat draws none. */
@Mixin(Screen.class)
public abstract class ScreenBlurMixin1201 {
    @Shadow protected Minecraft minecraft;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"))
    private void lads$menuBlur(GuiGraphics graphics, CallbackInfo ci) {
        if (minecraft != null) ScreenEffects1201.menuBackground(minecraft, (Screen) (Object) this, graphics);
    }
}
