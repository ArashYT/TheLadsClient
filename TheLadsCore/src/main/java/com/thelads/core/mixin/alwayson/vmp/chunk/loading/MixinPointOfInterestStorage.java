/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.datafixers.util.Pair
 *  com.mojang.serialization.Codec
 *  it.unimi.dsi.fastutil.longs.LongSet
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.core.SectionPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.ai.village.poi.PoiManager
 *  net.minecraft.world.entity.ai.village.poi.PoiSection
 *  net.minecraft.world.entity.ai.village.poi.PoiSection$Packed
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LevelHeightAccessor
 *  net.minecraft.world.level.chunk.status.ChunkStatus
 *  net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter
 *  net.minecraft.world.level.chunk.storage.SectionStorage
 *  net.minecraft.world.level.chunk.storage.SimpleRegionStorage
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.thelads.core.mixin.alwayson.vmp.chunk.loading;

import com.thelads.core.features.alwayson.vmp.common.chunk.loading.IPOIAsyncPreload;
import com.thelads.core.features.alwayson.vmp.common.chunk.loading.async_chunks_on_player_login.AsyncChunkLoadUtil;
import com.thelads.core.mixin.alwayson.vmp.access.IPointOfInterestSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={PoiManager.class})
public abstract class MixinPointOfInterestStorage
extends SectionStorage<PoiSection, PoiSection.Packed>
implements IPOIAsyncPreload {
    @Shadow
    @Final
    private LongSet loadedChunks;

    public MixinPointOfInterestStorage(SimpleRegionStorage storageAccess, Codec<PoiSection.Packed> codec, Function<PoiSection, PoiSection.Packed> serializer, BiFunction<PoiSection.Packed, Runnable, PoiSection> deserializer, Function<Runnable, PoiSection> factory, RegistryAccess registryManager, ChunkIOErrorReporter errorHandler, LevelHeightAccessor world) {
        super(storageAccess, codec, serializer, deserializer, factory, registryManager, errorHandler, world);
    }

    @Override
    public CompletableFuture<Void> preloadChunksAtAsync(ServerLevel world, BlockPos pos, int radius) {
        if (!world.getServer().isSameThread()) {
            return CompletableFuture.supplyAsync(() -> this.preloadChunksAtAsync(world, pos, radius), (Executor)world.getServer()).thenCompose(Function.identity());
        }
        CompletableFuture[] futures = (CompletableFuture[])SectionPos.aroundChunk((ChunkPos)ChunkPos.containing((BlockPos)pos), (int)Math.floorDiv(radius, 16), (int)this.levelHeightAccessor.getMinSectionY(), (int)this.levelHeightAccessor.getMaxSectionY())
            .map(sectionPos -> Pair.of((Object)sectionPos, (Object)this.getOrLoad(sectionPos.asLong())))
            .filter(pair -> !((Optional<PoiSection>) pair.getSecond()).map(pointOfInterestSet -> ((IPointOfInterestSet)pointOfInterestSet).invokeIsValid()).orElse(false))
            .map(pair -> ((SectionPos)pair.getFirst()).chunk())
            .filter(chunkPos -> !this.loadedChunks.contains(chunkPos.pack()))
            .map(chunkPos -> AsyncChunkLoadUtil.scheduleChunkLoadToStatus(world, chunkPos, ChunkStatus.EMPTY).whenCompleteAsync((either, unused1) -> either.ifSuccess(chunk -> this.loadedChunks.add(chunk.getPos().pack())), (Executor)world.getServer()))
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }
}

