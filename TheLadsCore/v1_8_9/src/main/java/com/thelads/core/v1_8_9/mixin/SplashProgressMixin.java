package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.gui.LadsSplash189;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge's mod-loading splash thread (SplashProgress' anonymous Runnable) shows the Lads loading screen: drawn over Forge's
 * white frame just before each buffer swap, paced to the monitor's refresh rate instead of Forge's fixed 100 FPS.
 */
@Mixin(targets = "net.minecraftforge.fml.client.SplashProgress$3", remap = false)
public abstract class SplashProgressMixin {
    @Unique private static int ladsRefreshRate;

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Semaphore;acquireUninterruptibly()V"), require = 1)
    private void ladsDrawFrame(CallbackInfo ci) {
        LadsSplash189.frame();
    }

    @ModifyArg(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V"), require = 1)
    private int ladsFrameRate(int forge) {
        if (ladsRefreshRate == 0) ladsRefreshRate = Math.max(60, Display.getDesktopDisplayMode().getFrequency());
        return ladsRefreshRate;
    }

    /** The last clearGL releases the splash context: free the logo just before. */
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/client/SplashProgress$3;clearGL()V", ordinal = 1), require = 1)
    private void ladsFinish(CallbackInfo ci) {
        LadsSplash189.finish();
    }
}
