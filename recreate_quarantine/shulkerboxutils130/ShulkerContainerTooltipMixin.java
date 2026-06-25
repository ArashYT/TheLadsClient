package com.thelads.core.mixin.auto.shulkerboxutils130;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ShulkerContainerTooltipMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    public void onRender(CallbackInfo ci) {
        // Example of a safe no-op injection
    }
}
