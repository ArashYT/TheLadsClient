package com.thelads.core.mixin;

import net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

@Mixin(ConfirmExperimentalFeaturesScreen.class)
public class ConfirmExperimentalFeaturesScreenMixin {
    @Shadow @Final private BooleanConsumer callback;

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        if (com.thelads.core.client.benchmark.BenchmarkTracker.ENABLED) {
            System.out.println("[Benchmark] Auto-confirming ConfirmExperimentalFeaturesScreen");
            this.callback.accept(true);
        }
    }
}
