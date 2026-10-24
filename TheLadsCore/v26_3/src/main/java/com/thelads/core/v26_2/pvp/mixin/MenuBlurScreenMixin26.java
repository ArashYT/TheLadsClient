package com.thelads.core.v26_2.pvp.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thelads.core.v26_2.pvp.PvpVisuals26;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menu Blur on 26.x: vanilla blurs menus that are not in-game UI by its Menu Background Blur; while the module is on, its toggles
 * decide per kind of screen, and inventories and other in-game UI (which vanilla only dims) get the same blur before their dim.
 */
@Mixin(Screen.class)
public abstract class MenuBlurScreenMixin26 {
    @Shadow @Final protected Minecraft minecraft;
    @Shadow public abstract boolean isInGameUi();

    @Inject(method = "extractBackground", at = @At("HEAD"))
    private void lads$inGameBlur(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (isInGameUi()) PvpVisuals26.inGameBlur(minecraft, (Screen) (Object) this, graphics);
    }

    @ModifyExpressionValue(method = "extractBlurredBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I"))
    private int lads$menuBlur(int vanilla) {
        return PvpVisuals26.menuBlur(minecraft, (Screen) (Object) this, vanilla);
    }
}
