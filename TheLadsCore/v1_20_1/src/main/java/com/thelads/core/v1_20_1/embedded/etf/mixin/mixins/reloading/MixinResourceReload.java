package com.thelads.core.v1_20_1.embedded.etf.mixin.mixins.reloading;

import net.minecraft.client.ResourceLoadStateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_20_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_20_1.embedded.etf.utils.ETFUtils2;

@Mixin(ResourceLoadStateTracker.class)
public abstract class MixinResourceReload {


    @Inject(method = "finishReload", at = @At("HEAD"))
    private void etf$injected(CallbackInfo ci) {
        ETFUtils2.logMessage("reloading ETF data.");
        ETFManager.resetInstance();
    }
}

