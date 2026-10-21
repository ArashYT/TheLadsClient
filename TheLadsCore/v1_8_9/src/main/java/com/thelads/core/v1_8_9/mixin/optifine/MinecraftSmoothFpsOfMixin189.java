package com.thelads.core.v1_8_9.mixin.optifine;

import com.thelads.core.modules.OptiFine189Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * OptiFine Smooth FPS setting: flushes OpenGL pipeline via glFinish at the end of each frame,
 * eliminating GPU command queue backlog and micro-stutters.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSmoothFpsOfMixin189 {
    @Inject(method = "updateDisplay", at = @At("RETURN"))
    private void lads$smoothFpsFlush(CallbackInfo ci) {
        if (OptiFine189Module.sOfSmoothFps) {
            GL11.glFinish();
        }
    }

    @Inject(method = "checkGLError", at = @At("HEAD"), cancellable = true)
    private void lads$checkGlError(String message, CallbackInfo ci) {
        if (!OptiFine189Module.sOfShowGlErrors) {
            ci.cancel();
        }
    }
}
