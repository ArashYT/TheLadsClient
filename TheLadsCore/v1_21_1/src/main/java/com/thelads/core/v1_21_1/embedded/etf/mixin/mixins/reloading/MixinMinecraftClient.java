package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.reloading;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;


@Mixin(Minecraft.class)
public abstract class MixinMinecraftClient {


    @Inject(method = "disconnect()V", at = @At("TAIL"))
    private void etf$injected(CallbackInfo ci) {
        ETFManager.resetInstance();
    }
}

