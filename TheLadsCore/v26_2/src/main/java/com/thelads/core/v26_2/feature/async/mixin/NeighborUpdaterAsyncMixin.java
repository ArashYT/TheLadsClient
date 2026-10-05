package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;

/** Neighbour updates (redstone) run through one stack per level, also outside setBlock (a pressure plate's update). */
@Mixin(CollectingNeighborUpdater.class)
abstract class NeighborUpdaterAsyncMixin {
    @WrapMethod(method = "addAndRun")
    private void lads$lockUpdates(BlockPos pos, CollectingNeighborUpdater.NeighborUpdates update, Operation<Void> original) {
        AsyncTicking.locked(original, pos, update);
    }
}
