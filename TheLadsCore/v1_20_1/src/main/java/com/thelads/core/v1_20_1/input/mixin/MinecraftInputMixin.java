package com.thelads.core.v1_20_1.input.mixin;

import com.thelads.core.v1_20_1.input.InputLatency1201;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftInputMixin {
    /** Before the frame's ticks and camera read input. */
    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void lads$frameStart(boolean tick, CallbackInfo ci) {
        InputLatency1201.frameStart();
    }
}
