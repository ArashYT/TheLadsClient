package com.thelads.core.mixin;

import net.minecraft.client.gui.screens.BackupConfirmScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackupConfirmScreen.class)
public class BackupConfirmScreenMixin {
    @Shadow @Final protected BackupConfirmScreen.Listener onProceed;

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        if (com.thelads.core.client.benchmark.BenchmarkTracker.ENABLED) {
            System.out.println("[Benchmark] Auto-confirming BackupConfirmScreen: bypass backup and proceed");
            this.onProceed.proceed(false, false);
        }
    }
}
