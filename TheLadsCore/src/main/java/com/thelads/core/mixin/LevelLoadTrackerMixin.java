package com.thelads.core.mixin;

import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelLoadTracker.class)
public class LevelLoadTrackerMixin {
    @ModifyVariable(method = "<init>(J)V", at = @At("HEAD"), argsOnly = true)
    private static long modifyCloseDelayMs(long closeDelayMs) {
        return 0L;
    }
}
