/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LocalMobCapCalculator
 *  net.minecraft.world.level.LocalMobCapCalculator$MobCounts
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_player_lookups;

import com.thelads.core.features.alwayson.vmp.common.chunkwatching.AreaPlayerChunkWatchingManager;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.TACSExtension;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LocalMobCapCalculator.class}, priority=950)
public abstract class MixinSpawnDensityCapper {
    @Shadow
    @Final
    private ChunkMap chunkMap;
    @Mutable
    @Shadow
    @Final
    private Map<ServerPlayer, LocalMobCapCalculator.MobCounts> playerMobCounts;
    private static final Function<ServerPlayer, LocalMobCapCalculator.MobCounts> newDensityCap = ignored -> new LocalMobCapCalculator.MobCounts();

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void onInit(CallbackInfo info) {
        this.playerMobCounts = new Reference2ReferenceOpenHashMap();
    }

    @Unique
    private Object[] getMobSpawnablePlayersArray(ChunkPos chunkPos) {
        AreaPlayerChunkWatchingManager manager = ((TACSExtension)this.chunkMap).getAreaPlayerChunkWatchingManager();
        return manager.getPlayersInGeneralAreaMap(chunkPos.pack());
    }

    private static double sqrDistance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    @Overwrite
    public void addMob(ChunkPos chunkPos, MobCategory spawnGroup) {
        double centerX = chunkPos.getMiddleBlockX();
        double centerZ = chunkPos.getMiddleBlockZ();
        for (Object _player : this.getMobSpawnablePlayersArray(chunkPos)) {
            ServerPlayer player;
            if (!(_player instanceof ServerPlayer) || (player = (ServerPlayer)_player).isSpectator() || !(MixinSpawnDensityCapper.sqrDistance(centerX, centerZ, player.getX(), player.getZ()) <= 16384.0)) continue;
            this.playerMobCounts.computeIfAbsent(player, newDensityCap).add(spawnGroup);
        }
    }

    @Overwrite
    public boolean canSpawn(MobCategory spawnGroup, ChunkPos chunkPos) {
        double centerX = chunkPos.getMiddleBlockX();
        double centerZ = chunkPos.getMiddleBlockZ();
        for (Object _player : this.getMobSpawnablePlayersArray(chunkPos)) {
            LocalMobCapCalculator.MobCounts densityCap;
            ServerPlayer player;
            if (!(_player instanceof ServerPlayer) || (player = (ServerPlayer)_player).isSpectator() || !(MixinSpawnDensityCapper.sqrDistance(centerX, centerZ, player.getX(), player.getZ()) <= 16384.0) || (densityCap = this.playerMobCounts.get(player)) != null && !densityCap.canSpawn(spawnGroup)) continue;
            return true;
        }
        return false;
    }
}

