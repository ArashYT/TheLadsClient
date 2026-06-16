package com.thelads.core.mixin.alwayson.hyperlaunch;

import com.thelads.core.features.alwayson.hyperlaunch.HyperLaunch;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.ExecutorService;

@Mixin(Util.class)
public class UtilMixin {
    @Inject(method = "bootstrapExecutor", at = @At("HEAD"), cancellable = true)
    private static void hyperlaunch$useDedicatedBootstrapExecutor(CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue((ExecutorService) HyperLaunch.bootstrapExecutor());
    }

    @Inject(method = "backgroundExecutor", at = @At("HEAD"), cancellable = true)
    private static void hyperlaunch$useDedicatedBackgroundExecutor(CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue((ExecutorService) HyperLaunch.minecraftBootstrapExecutor());
    }

    @Inject(method = "ioPool", at = @At("HEAD"), cancellable = true)
    private static void hyperlaunch$useDedicatedIoExecutor(CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue((ExecutorService) HyperLaunch.minecraftIoExecutor());
    }

    @Inject(method = "nonCriticalIoPool", at = @At("HEAD"), cancellable = true)
    private static void hyperlaunch$useDedicatedNonCriticalIoExecutor(CallbackInfoReturnable<ExecutorService> cir) {
        cir.setReturnValue((ExecutorService) HyperLaunch.minecraftIoExecutor());
    }

    @Inject(method = "maxAllowedExecutorThreads", at = @At("HEAD"), cancellable = true)
    private static void hyperlaunch$increaseExecutorThreadLimit(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(HyperLaunch.maxBackgroundThreads());
    }
}
