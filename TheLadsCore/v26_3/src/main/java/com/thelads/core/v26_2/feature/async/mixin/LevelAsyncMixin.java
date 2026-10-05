package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelAsyncMixin {
    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean lads$lockSetBlock(BlockPos pos, BlockState state, int flags, int limit, Operation<Boolean> original) {
        return AsyncTicking.locked(original, pos, state, flags, limit);
    }

    @WrapMethod(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z")
    private boolean lads$lockDestroyBlock(BlockPos pos, boolean drop, Entity breaker, int limit, Operation<Boolean> original) {
        return AsyncTicking.locked(original, pos, drop, breaker, limit);
    }

    @WrapMethod(method = "nextSubTickCount")
    private long lads$lockSubTick(Operation<Long> original) {
        return AsyncTicking.locked(original);
    }

    /** Vanilla answers null off the main thread; a worker reads it (creating a pending one, as vanilla would) under the lock. */
    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void lads$workerBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        Level level = (Level) (Object) this;
        if (!AsyncTicking.onWorker() || level.isClientSide()) return;
        AsyncTicking.lock();
        try {
            cir.setReturnValue(level.isInValidBounds(pos) ? level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) : null);
        } finally {
            AsyncTicking.unlock();
        }
    }
}
