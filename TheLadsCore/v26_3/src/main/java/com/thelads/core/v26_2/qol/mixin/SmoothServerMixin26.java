package com.thelads.core.v26_2.qol.mixin;

import com.thelads.core.perf.SmoothSingleplayer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smooth Singleplayer tick phase lock: after each integrated-server tick, nudge the next tick's start by a few ms toward just after
 * the client tick. Never waits. Async (feature/async) only runs entity ticks inside this tick and touches neither this timestamp nor
 * the tracker, so the two do not fight: a slower Async tick just makes the next-tick time already due, which drops the lock.
 */
@Mixin(MinecraftServer.class)
abstract class SmoothServerMixin26 {
    @Shadow private long nextTickTimeNanos;

    @Inject(method = "tickServer", at = @At("TAIL"), require = 0)
    private void lads$phaseLock(BooleanSupplier haveTime, CallbackInfo ci) {
        if (!SmoothSingleplayer.tracking() || !((Object) this instanceof IntegratedServer server) || server.isPublished()) return;
        long adjust = SmoothSingleplayer.phaseAdjust(nextTickTimeNanos + (System.nanoTime() - net.minecraft.util.Util.getNanos()), server.tickRateManager().nanosecondsPerTick());
        if (adjust != 0L) nextTickTimeNanos += adjust;
    }
}
