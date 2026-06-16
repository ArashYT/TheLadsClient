/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.ibm.asyncutil.locks.AsyncSemaphore
 *  com.ibm.asyncutil.locks.FairAsyncSemaphore
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.server.level.ChunkLevel
 *  net.minecraft.server.level.ChunkResult
 *  net.minecraft.server.level.FullChunkStatus
 *  net.minecraft.server.level.ServerChunkCache
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.Ticket
 *  net.minecraft.server.level.TicketType
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.TicketStorage
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.status.ChunkStatus
 */
package com.thelads.core.features.alwayson.vmp.common.chunk.loading.async_chunks_on_player_login;

import com.thelads.core.features.alwayson.vmp.common.util.SimpleAsyncSemaphore;
import com.thelads.core.mixin.alwayson.vmp.access.IServerChunkManager;
import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class AsyncChunkLoadUtil {
    private static final TicketType ASYNC_CHUNK_LOAD = new TicketType(0L, 2);
    public static final SimpleAsyncSemaphore SEMAPHORE = new SimpleAsyncSemaphore(12L);
    private static final ThreadLocal<Boolean> isRespawnChunkLoadFinished = ThreadLocal.withInitial(() -> false);

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoad(ServerLevel world, ChunkPos pos) {
        return AsyncChunkLoadUtil.scheduleChunkLoadWithRadius(world, pos, 3);
    }

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoadWithRadius(ServerLevel world, ChunkPos pos, int radius) {
        return AsyncChunkLoadUtil.scheduleChunkLoadWithLevel(world, pos, 33 - radius);
    }

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoadToStatus(ServerLevel world, ChunkPos pos, ChunkStatus status) {
        return AsyncChunkLoadUtil.scheduleChunkLoadWithLevel(world, pos, ChunkLevel.byStatus((ChunkStatus)status));
    }

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoadWithLevel(ServerLevel world, ChunkPos pos, int level) {
        ServerChunkCache chunkManager = world.getChunkSource();
        TicketStorage ticketManager = ((IServerChunkManager)chunkManager).getTicketManager();
        Ticket chunkTicket = new Ticket(ASYNC_CHUNK_LOAD, level);
        CompletableFuture<ChunkResult<ChunkAccess>> future = SEMAPHORE.acquire().thenComposeAsync(unused -> {
            ticketManager.addTicket(chunkTicket, pos);
            ((IServerChunkManager)chunkManager).invokeUpdateChunks();
            ChunkHolder chunkHolder = ((IThreadedAnvilChunkStorage)chunkManager.chunkMap).invokeGetCurrentChunkHolder(pos.pack());
            if (chunkHolder == null) {
                throw new IllegalStateException("Chunk not there when requested");
            }
            FullChunkStatus levelType = ChunkLevel.fullStatus((int)level);
            return (CompletableFuture<ChunkResult<ChunkAccess>>) (CompletableFuture) switch (levelType) {
                default -> throw new IncompatibleClassChangeError();
                case FullChunkStatus.INACCESSIBLE -> chunkHolder.scheduleChunkGenerationTask(ChunkLevel.generationStatus((int)level), world.getChunkSource().chunkMap);
                case FullChunkStatus.FULL -> chunkHolder.getFullChunkFuture().thenApply(either -> either);
                case FullChunkStatus.BLOCK_TICKING -> chunkHolder.getTickingChunkFuture().thenApply(either -> either);
                case FullChunkStatus.ENTITY_TICKING -> chunkHolder.getEntityTickingChunkFuture().thenApply(either -> either);
            };
        }, (Executor)world.getServer());
        future.whenCompleteAsync((unused, throwable) -> {
            SEMAPHORE.release();
            if (throwable != null) {
                throwable.printStackTrace();
            }
            ticketManager.removeTicket(chunkTicket, pos);
        }, (Executor)world.getServer());
        return (CompletableFuture<ChunkResult<ChunkAccess>>) future;
    }

    public static void setIsRespawnChunkLoadFinished(boolean value) {
        isRespawnChunkLoadFinished.set(value);
    }

    public static boolean isRespawnChunkLoadFinished() {
        return isRespawnChunkLoadFinished.get();
    }
}

