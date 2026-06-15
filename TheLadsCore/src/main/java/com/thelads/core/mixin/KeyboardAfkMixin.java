package com.thelads.core.mixin;

import com.thelads.core.modules.DynamicFPSModule;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resets AFK timer on any key press. */
@Mixin(KeyboardHandler.class)
public class KeyboardAfkMixin {
    // MC 26.1.2: keyPress(long, int, int, int, int) became keyPress(long, int, KeyEvent)
    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void ladsKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("DynamicFPS");
        if (m instanceof DynamicFPSModule dynFps) dynFps.onInput();
    }
}
