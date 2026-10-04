package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerLevelAsyncMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void lads$tickEntities(EntityTickList list, Consumer<Entity> action, Operation<Void> original) {
        AsyncTicking.tickEntities((ServerLevel) (Object) this, list, action, original);
    }

    /** The vanilla level random throws when two threads use it at once; the thread-safe kind draws the same way. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void lads$threadSafeRandom(CallbackInfo ci) {
        ((LevelRandomAccessor) this).lads$setRandom(RandomSource.createThreadSafe());
    }

    /** Client block updates and path invalidation visit every navigating mob: after the phase, on the main thread. */
    @WrapMethod(method = "sendBlockUpdated")
    private void lads$deferBlockUpdate(BlockPos pos, BlockState old, BlockState current, int flags, Operation<Void> original) {
        if (!AsyncTicking.onWorker()) { original.call(pos, old, current, flags); return; }
        ServerLevel level = (ServerLevel) (Object) this;
        level.getPathTypeCache().invalidate(pos);
        BlockPos at = pos.immutable();
        AsyncTicking.defer(() -> level.sendBlockUpdated(at, old, current, flags));
    }

    @WrapMethod(method = "blockEvent")
    private void lads$lockBlockEvent(BlockPos pos, Block block, int a, int b, Operation<Void> original) {
        AsyncTicking.locked(original, pos, block, a, b);
    }

    @WrapMethod(method = "explode")
    private void lads$lockExplosion(Entity source, DamageSource damageSource, ExplosionDamageCalculator calculator, double x, double y, double z,
                                    float radius, boolean fire, Level.ExplosionInteraction interaction, ParticleOptions small, ParticleOptions large,
                                    WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> sound, Operation<Void> original) {
        AsyncTicking.locked(original, source, damageSource, calculator, x, y, z, radius, fire, interaction, small, large, blockParticles, sound);
    }
}
