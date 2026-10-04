package com.thelads.core.v26_2.feature.async.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A changed block or block entity marks its chunk for saving in one set per level (C2ME also refuses that off the main
 * thread): queued for the end of the phase.
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapAsyncMixin {
    @WrapMethod(method = "setChunkUnsaved")
    private void lads$deferUnsaved(ChunkPos pos, Operation<Void> original) {
        if (AsyncTicking.onWorker()) AsyncTicking.defer(() -> original.call(pos));
        else original.call(pos);
    }
}
