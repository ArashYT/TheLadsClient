/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.Holder
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.core.SectionPos
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ServerChunkCache
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.WorldGenLevel
 *  net.minecraft.world.level.dimension.DimensionType
 *  net.minecraft.world.level.storage.WritableLevelData
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_player_lookups;

import com.thelads.core.features.alwayson.vmp.common.chunkwatching.AreaPlayerChunkWatchingManager;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.TACSExtension;
import io.papermc.paper.util.MCUtil;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={ServerLevel.class})
public abstract class MixinServerWorld
extends Level
implements WorldGenLevel {
    protected MixinServerWorld(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
    }

    @Shadow
    public abstract ServerChunkCache getChunkSource();

    @Nullable
    public Player getNearestPlayer(double x, double y, double z, double maxDistance, @Nullable Predicate<Entity> targetPredicate) {
        ChunkMap threadedAnvilChunkStorage = this.getChunkSource().chunkMap;
        AreaPlayerChunkWatchingManager playerChunkWatchingManager = ((TACSExtension)threadedAnvilChunkStorage).getAreaPlayerChunkWatchingManager();
        int chunkX = SectionPos.posToSectionCoord((double)x);
        int chunkZ = SectionPos.posToSectionCoord((double)z);
        if ((double)(AreaPlayerChunkWatchingManager.GENERAL_PLAYER_AREA_MAP_DISTANCE * 16) < maxDistance || maxDistance < 0.0) {
            return super.getNearestPlayer(x, y, z, maxDistance, targetPredicate);
        }
        Object[] playersWatchingChunkArray = playerChunkWatchingManager.getPlayersInGeneralAreaMap(MCUtil.getCoordinateKey(chunkX, chunkZ));
        ServerPlayer nearestPlayer = null;
        double nearestDistance = maxDistance * maxDistance;
        for (Object __player : playersWatchingChunkArray) {
            double distance;
            if (!(__player instanceof ServerPlayer)) continue;
            ServerPlayer player = (ServerPlayer)__player;
            if (targetPredicate != null && !targetPredicate.test((Entity)player) || !((distance = player.distanceToSqr(x, y, z)) < nearestDistance)) continue;
            nearestDistance = distance;
            nearestPlayer = player;
        }
        return nearestPlayer;
    }

    public boolean hasNearbyAlivePlayer(double x, double y, double z, double range) {
        ChunkMap threadedAnvilChunkStorage = this.getChunkSource().chunkMap;
        AreaPlayerChunkWatchingManager playerChunkWatchingManager = ((TACSExtension)threadedAnvilChunkStorage).getAreaPlayerChunkWatchingManager();
        int chunkX = SectionPos.posToSectionCoord((double)x);
        int chunkZ = SectionPos.posToSectionCoord((double)z);
        if ((double)(AreaPlayerChunkWatchingManager.GENERAL_PLAYER_AREA_MAP_DISTANCE * 16) < range) {
            return super.hasNearbyAlivePlayer(x, y, z, range);
        }
        Object[] playersWatchingChunkArray = playerChunkWatchingManager.getPlayersWatchingChunkArray(MCUtil.getCoordinateKey(chunkX, chunkZ));
        double rangeSquared = range * range;
        for (Object __player : playersWatchingChunkArray) {
            ServerPlayer player;
            if (!(__player instanceof ServerPlayer) || (player = (ServerPlayer)__player).isSpectator() || !player.isAlive()) continue;
            double distance = player.distanceToSqr(x, y, z);
            if (!(range < 0.0) && !(distance < rangeSquared)) continue;
            return true;
        }
        return false;
    }
}

