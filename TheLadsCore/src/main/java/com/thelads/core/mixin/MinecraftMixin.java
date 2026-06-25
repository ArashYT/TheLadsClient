package com.thelads.core.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.client.benchmark.BenchmarkTracker;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "<init>", at = @At("HEAD"))
    private static void onInit(GameConfig gameConfig, CallbackInfo ci) {
        BenchmarkTracker.setMinecraftInitTime(System.nanoTime());
    }

    @Inject(method = "run", at = @At("TAIL"))
    private void onRun(CallbackInfo ci) {
        if (BenchmarkTracker.ENABLED) {
            System.out.println("[Benchmark] Minecraft.run() finished. Forcing System.exit(0) to bypass hanging non-daemon threads.");
            System.exit(0);
        }
    }
}
