package com.thelads.core.v26_2.qol.mixin;

import com.thelads.core.perf.SmoothSingleplayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.UpdateInterval;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth Singleplayer entity tracking: a private integrated server sends entity position/rotation every tick instead of every 2-20 ticks. */
@Mixin(ServerEntity.class)
abstract class SmoothEntityMixin26 {
    @Unique private static final UpdateInterval LADS_EVERY_TICK = UpdateInterval.periodic(1);
    @Shadow @Mutable private UpdateInterval updateInterval;
    @Unique private UpdateInterval lads$vanillaInterval;

    @Inject(method = "sendChanges", at = @At("HEAD"), require = 0)
    private void lads$everyTick(CallbackInfo ci) {
        if (lads$vanillaInterval == null) lads$vanillaInterval = updateInterval;
        // NEVER (entities that only move when told to) stays as it is
        updateInterval = SmoothSingleplayer.tracking() && lads$vanillaInterval != UpdateInterval.NEVER ? LADS_EVERY_TICK : lads$vanillaInterval;
    }
}
