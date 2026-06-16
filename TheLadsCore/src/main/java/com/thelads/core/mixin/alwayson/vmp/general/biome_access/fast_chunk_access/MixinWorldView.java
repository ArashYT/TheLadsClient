/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.server.level.ChunkResult
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LevelReader
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraft.world.level.chunk.status.ChunkStatus
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.general.biome_access.fast_chunk_access;

import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorage;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={LevelReader.class})
public interface MixinWorldView {
    @Redirect(method={"getNoiseBiome"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/LevelReader;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess redirectBiomeChunk(LevelReader instance, int x, int z, ChunkStatus chunkStatus, boolean create) {
        if (!create && instance instanceof ServerLevel) {
            LevelChunk chunk;
            CompletableFuture future;
            ChunkResult either;
            ServerLevel world = (ServerLevel)instance;
            ChunkHolder holder = ((IThreadedAnvilChunkStorage)world.getChunkSource().chunkMap).invokeGetChunkHolder(ChunkPos.pack((int)x, (int)z));
            if (holder != null && (either = (ChunkResult)(future = holder.getFullChunkFuture()).getNow(null)) != null && (chunk = (LevelChunk)either.orElse(null)) != null) {
                return chunk;
            }
        }
        return instance.getChunk(x, z, chunkStatus, create);
    }
}

