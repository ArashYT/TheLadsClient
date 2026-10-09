package com.thelads.core.v26_2.input.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.v26_2.input.InputLatency26;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** InputLatency26's two hooks. */
public final class InputLatencyMixins26 {
    private InputLatencyMixins26() {}

    @Mixin(RenderSystem.class)
    public static class Poll {
        @Inject(method = "pollEvents", at = @At("HEAD"), require = 0)
        private static void lads$frameStart(SDLEventHandler handler, CallbackInfo ci) {
            InputLatency26.frameStart();
        }
    }

    @Mixin(Minecraft.class)
    public static class Submit {
        @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;submit()V"), require = 0)
        private void lads$beforeSubmit(boolean advanceGameTime, CallbackInfo ci) {
            InputLatency26.beforeSubmit();
        }

        @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;present()V", shift = At.Shift.AFTER), require = 0)
        private void lads$afterPresent(boolean advanceGameTime, CallbackInfo ci) {
            InputLatency26.afterPresent();
        }
    }
}
