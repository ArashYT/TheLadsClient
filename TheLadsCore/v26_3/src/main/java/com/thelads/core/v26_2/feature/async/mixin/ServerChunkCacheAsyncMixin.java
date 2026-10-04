package com.thelads.core.v26_2.feature.async.mixin;

import com.thelads.core.v26_2.feature.async.AsyncTicking;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Off the main thread vanilla waits for the main thread, which is waiting for the workers: workers read loaded chunks directly. */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheAsyncMixin {
    @Shadow protected abstract ChunkHolder getVisibleChunkIfPresent(long key);

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void lads$workerChunk(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        if (!AsyncTicking.onWorker()) return;
        ChunkHolder holder = getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        ChunkAccess chunk = holder == null ? null : holder.getChunkIfPresent(status);
        if (chunk == null && load) throw AsyncTicking.unsafe("chunk " + x + ", " + z + " would have to be loaded");
        cir.setReturnValue(chunk);
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void lads$workerChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (!AsyncTicking.onWorker()) return;
        ChunkHolder holder = getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        cir.setReturnValue(holder != null && holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk chunk ? chunk : null);
    }
}
