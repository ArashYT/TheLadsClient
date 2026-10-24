package com.thelads.core.v1_8_9.pvp.mixin;

import com.thelads.core.v1_8_9.pvp.ScreenEffects189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menu Blur: a menu's world background starts (before vanilla's dim) with the world and HUD behind it blurred. Chat draws none. */
@Mixin(GuiScreen.class)
public abstract class GuiScreenBlurMixin189 {
    @Shadow public Minecraft mc;

    @Inject(method = "drawWorldBackground", at = @At("HEAD"))
    private void lads$menuBlur(int tint, CallbackInfo ci) {
        if (mc != null && mc.theWorld != null) ScreenEffects189.menuBackground(mc, (GuiScreen) (Object) this);
    }
}
