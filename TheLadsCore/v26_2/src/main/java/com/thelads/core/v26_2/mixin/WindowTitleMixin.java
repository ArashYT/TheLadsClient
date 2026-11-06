package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.WindowTitle26;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
    @Inject(method = "updateTitle()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$updateTitle(CallbackInfo ci) {
        WindowTitle26.refresh((Minecraft) (Object) this);
        ci.cancel();
    }

    @Inject(method = "setScreenAndShow", at = @At("TAIL"), require = 1)
    private void lads$afterSetScreenAndShow(Screen screen, CallbackInfo ci) {
        WindowTitle26.refresh((Minecraft) (Object) this);
    }

    @Inject(method = "tick()V", at = @At("TAIL"), require = 1)
    private void lads$afterTick(CallbackInfo ci) {
        WindowTitle26.refresh((Minecraft) (Object) this);
    }
}
