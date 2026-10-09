package com.thelads.core.v1_8_9.input.mixin;

import com.thelads.core.v1_8_9.input.InputLatency189;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftInputMixin189 {
    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void ladsFrameStart(CallbackInfo ci) {
        InputLatency189.frameStart();
    }

    @Inject(method = "updateDisplay", at = @At("HEAD"))
    private void ladsBeforeSwap(CallbackInfo ci) {
        InputLatency189.beforeSwap();
    }
}
