package com.thelads.core.v1_20_1.qol.mixin;

import com.thelads.core.perf.SmoothSingleplayer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth Singleplayer tick phase lock: after each integrated-server tick, nudge the next tick's start by a few ms toward just after the client tick. Never waits. */
@Mixin(MinecraftServer.class)
abstract class SmoothServerMixin1201 {
    @Shadow private long nextTickTime;

    @Inject(method = "tickServer", at = @At("TAIL"), require = 0)
    private void lads$phaseLock(BooleanSupplier haveTime, CallbackInfo ci) {
        if (!SmoothSingleplayer.tracking() || !((Object) this instanceof IntegratedServer server) || server.isPublished()) return;
        long adjust = SmoothSingleplayer.phaseAdjust(nextTickTime * 1_000_000L + (System.nanoTime() - net.minecraft.Util.getNanos()), 50_000_000L);
        if (adjust != 0L) nextTickTime += Math.round(adjust / 1_000_000.0);
    }
}
