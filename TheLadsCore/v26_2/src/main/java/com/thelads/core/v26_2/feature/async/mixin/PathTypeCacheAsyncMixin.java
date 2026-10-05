package com.thelads.core.v26_2.feature.async.mixin;

import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The level's path type cache stores a position and its type in two arrays; two threads writing one slot would pair a
 * position with another position's type. Workers only read it and compute misses without storing them.
 */
@Mixin(PathTypeCache.class)
abstract class PathTypeCacheAsyncMixin {
    @Shadow private static int index(long key) { throw new AssertionError(); }
    @Shadow private PathType get(int index, long key) { throw new AssertionError(); }

    @Inject(method = "getOrCompute", at = @At("HEAD"), cancellable = true)
    private void lads$readOnly(BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> cir) {
        if (!AsyncTicking.onWorker()) return;
        long key = pos.asLong();
        PathType cached = get(index(key), key);
        cir.setReturnValue(cached != null ? cached : WalkNodeEvaluator.getPathTypeFromState(level, pos));
    }
}
