package com.thelads.ladsbench.v1201.mixin;

import com.thelads.ladsbench.Bench;
import com.thelads.ladsbench.v1201.Game1201;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void ladsbench$frame(boolean advanceGameTime, CallbackInfo ci) { Bench.frame(); }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ladsbench$tick(CallbackInfo ci) { Bench.tick(Game1201.INSTANCE); }
}
