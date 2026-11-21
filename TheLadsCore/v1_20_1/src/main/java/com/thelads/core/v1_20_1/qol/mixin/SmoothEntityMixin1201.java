package com.thelads.core.v1_20_1.qol.mixin;

import com.thelads.core.perf.SmoothSingleplayer;
import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth Singleplayer entity tracking: a private integrated server sends entity position/rotation every tick instead of every 2-20 ticks. */
@Mixin(ServerEntity.class)
abstract class SmoothEntityMixin1201 {
    @Shadow @Mutable private int updateInterval;
    @Unique private int lads$vanillaInterval = -1;

    @Inject(method = "sendChanges", at = @At("HEAD"), require = 0)
    private void lads$everyTick(CallbackInfo ci) {
        if (lads$vanillaInterval < 0) lads$vanillaInterval = updateInterval;
        updateInterval = SmoothSingleplayer.tracking() && lads$vanillaInterval > 1 && lads$vanillaInterval <= 20 ? 1 : lads$vanillaInterval;
    }
}
