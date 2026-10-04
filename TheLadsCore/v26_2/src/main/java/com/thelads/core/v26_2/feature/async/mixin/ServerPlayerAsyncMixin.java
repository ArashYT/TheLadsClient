package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

/** Players tick on the main thread, but mobs on two workers can hurt one player or credit stats to them at once. */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerAsyncMixin {
    @WrapMethod(method = "hurtServer")
    private boolean lads$lockHurt(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        return AsyncTicking.locked(original, level, source, damage);
    }

    @WrapMethod(method = "awardStat")
    private void lads$lockAward(Stat<?> stat, int count, Operation<Void> original) {
        AsyncTicking.locked(original, stat, count);
    }

    @WrapMethod(method = "resetStat")
    private void lads$lockReset(Stat<?> stat, Operation<Void> original) {
        AsyncTicking.locked(original, stat);
    }
}
