package com.thelads.core.mixin;

import com.thelads.core.modules.DynamicFPSModule;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resets AFK timer on mouse movement or clicks. */
@Mixin(MouseHandler.class)
public class MouseAfkMixin {
    @Inject(method = "onMove", at = @At("HEAD"), require = 0)
    private void ladsMouseMove(long window, double x, double y, CallbackInfo ci) {
        touchAfk();
    }

    // MC 26.1.2: onPress(long, int, int, int) became onButton(long, MouseButtonInfo, int)
    @Inject(method = "onButton", at = @At("HEAD"), require = 0)
    private void ladsMousePress(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        touchAfk();
    }

    private static void touchAfk() {
        Module m = ModuleManager.getInstance().getModule("DynamicFPS");
        if (m instanceof DynamicFPSModule dynFps) dynFps.onInput();
    }
}
