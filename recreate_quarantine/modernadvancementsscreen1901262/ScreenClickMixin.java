package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenClickMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), require = 0)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfo ci) {
        // Custom logic here
    }
}
