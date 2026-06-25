package com.thelads.core.mixin.auto.kerria1301211fabric;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class SodiumOptionsGUIMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Custom logic here
    }
}
