package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.WindowTitle1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class WindowTitleMixin1201 {
    @Inject(method = "updateTitle", at = @At("HEAD"), cancellable = true)
    private void lads$updateTitle(CallbackInfo ci) {
        WindowTitle1201.refresh((Minecraft) (Object) this);
        ci.cancel();
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void lads$afterSetScreen(Screen screen, CallbackInfo ci) {
        WindowTitle1201.refresh((Minecraft) (Object) this);
    }
}
