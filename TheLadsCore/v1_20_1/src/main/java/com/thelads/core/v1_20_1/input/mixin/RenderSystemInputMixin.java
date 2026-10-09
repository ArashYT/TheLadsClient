package com.thelads.core.v1_20_1.input.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v1_20_1.input.InputLatency1201;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** InputLatency1201's hooks in the frame's end: event polls, the swap and the frame limiter. */
@Mixin(value = RenderSystem.class, remap = false)
public class RenderSystemInputMixin {
    @Inject(method = "pollEvents()V", at = @At("HEAD"))
    private static void lads$beforePoll(CallbackInfo ci) {
        InputLatency1201.beforePoll();
    }

    @Inject(method = "flipFrame(J)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
    private static void lads$beforeSwap(long window, CallbackInfo ci) {
        InputLatency1201.beforeSwap();
    }

    @Inject(method = "limitDisplayFPS(I)V", at = @At("HEAD"), cancellable = true)
    private static void lads$limit(int fps, CallbackInfo ci) {
        if (InputLatency1201.limit(fps)) ci.cancel();
    }
}
