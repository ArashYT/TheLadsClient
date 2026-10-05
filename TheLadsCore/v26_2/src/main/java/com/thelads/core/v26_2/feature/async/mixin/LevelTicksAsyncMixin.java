package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;

/** Scheduled block and fluid ticks (pressure plates, tripwires) share one queue per level. */
@Mixin(LevelTicks.class)
abstract class LevelTicksAsyncMixin<T> {
    @WrapMethod(method = "schedule")
    private void lads$lockSchedule(ScheduledTick<T> tick, Operation<Void> original) {
        AsyncTicking.locked(original, tick);
    }
}
