package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.WindowTitle26;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class WindowTitleGuiMixin {
    @Inject(method = "setScreen", at = @At("TAIL"), require = 1)
    private void lads$afterGuiSetScreen(Screen screen, CallbackInfo ci) {
        WindowTitle26.refresh(Minecraft.getInstance());
    }
}
