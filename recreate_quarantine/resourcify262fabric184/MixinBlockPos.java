package com.thelads.core.mixin.auto.resourcify262fabric184;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockPos.class)
public class MixinBlockPos {

    @Inject(method = "manhattanDistanceTo", at = @At("HEAD"), require = 0)
    private void onManhattanDistanceTo(BlockPos blockPos, CallbackInfo ci) {
        // Minimal safe injection point
    }
}
