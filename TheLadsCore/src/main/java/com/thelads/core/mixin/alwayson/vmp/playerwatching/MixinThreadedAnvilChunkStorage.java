/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableList$Builder
 *  net.minecraft.core.SectionPos
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ChunkMap$DistanceManager
 *  net.minecraft.server.level.ChunkTrackingView
 *  net.minecraft.server.level.PlayerMap
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.util.TriState
 *  net.minecraft.world.level.ChunkPos
 *  org.slf4j.Logger
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching;

import com.google.common.collect.ImmutableList;
import com.thelads.core.features.alwayson.vmp.common.chunkwatching.AreaPlayerChunkWatchingManager;
import com.thelads.core.features.alwayson.vmp.common.config.Config;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.TACSExtension;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ChunkMap.class})
public abstract class MixinThreadedAnvilChunkStorage
implements TACSExtension {
    @Shadow
    @Final
    private PlayerMap playerMap;
    @Shadow
    private int serverViewDistance;
    @Shadow
    @Final
    private static Logger LOGGER;
    @Shadow
    @Final
    private ChunkMap.DistanceManager distanceManager;
    @Unique
    private AreaPlayerChunkWatchingManager areaPlayerChunkWatchingManager;

    @Shadow
    protected abstract boolean playerIsCloseEnoughForSpawning(ServerPlayer var1, ChunkPos var2);

    @Shadow
    protected abstract void markChunkPendingToSend(ServerPlayer var1, ChunkPos var2);

    @Shadow
    protected static void dropChunk(ServerPlayer player, ChunkPos pos) {
        throw new AbstractMethodError();
    }

    @Shadow
    protected abstract void updatePlayerPos(ServerPlayer var1);

    @Shadow
    abstract int getPlayerViewDistance(ServerPlayer var1);

    @Override
    public AreaPlayerChunkWatchingManager getAreaPlayerChunkWatchingManager() {
        return this.areaPlayerChunkWatchingManager;
    }

    @Inject(method={"<init>"}, at={@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap;setServerViewDistance(I)V")})
    private void redirectNewPlayerChunkWatchingManager(CallbackInfo ci) {
        this.areaPlayerChunkWatchingManager = new AreaPlayerChunkWatchingManager((player, chunkX, chunkZ) -> this.markChunkPendingToSend(player, new ChunkPos(chunkX, chunkZ)), (player, chunkX, chunkZ) -> MixinThreadedAnvilChunkStorage.dropChunk(player, new ChunkPos(chunkX, chunkZ)), (ChunkMap)(Object)this);
    }

    @Inject(method={"tick(Ljava/util/function/BooleanSupplier;)V"}, at={@At(value="RETURN")})
    private void onTick(CallbackInfo ci) {
        this.areaPlayerChunkWatchingManager.tick();
    }

    @Inject(method={"setServerViewDistance"}, at={@At(value="RETURN")})
    private void onSetViewDistance(CallbackInfo ci) {
        if (Config.SHOW_CHUNK_TRACKING_MESSAGES) {
            LOGGER.info("Changing watch distance to {}", (Object)this.serverViewDistance);
        }
        this.areaPlayerChunkWatchingManager.onWatchDistanceChange();
    }

    @Overwrite
    public List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyOnWatchDistanceEdge) {
        AreaPlayerChunkWatchingManager watchingManager = this.areaPlayerChunkWatchingManager;
        Object[] set = watchingManager.getPlayersWatchingChunkArray(chunkPos.pack());
        ImmutableList.Builder builder = ImmutableList.builder();
        int actualWatchDistance = this.serverViewDistance + 1;
        for (Object __player : set) {
            ServerPlayer serverPlayerEntity;
            SectionPos watchedPos;
            int chebyshevDistance;
            if (!(__player instanceof ServerPlayer) || (chebyshevDistance = Math.max(Math.abs((watchedPos = (serverPlayerEntity = (ServerPlayer)__player).getLastSectionPos()).x() - chunkPos.x()), Math.abs(watchedPos.z() - chunkPos.z()))) > actualWatchDistance || serverPlayerEntity.connection.chunkSender.isPending(chunkPos.pack()) || onlyOnWatchDistanceEdge && chebyshevDistance != actualWatchDistance) continue;
            builder.add((Object)serverPlayerEntity);
        }
        return builder.build();
    }

    @Overwrite
    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos pos) {
        long l = pos.pack();
        if (!this.distanceManager.hasPlayersNearby(l).toBoolean(true)) {
            return List.of();
        }
        ImmutableList.Builder builder = ImmutableList.builder();
        for (Object __player : this.areaPlayerChunkWatchingManager.getPlayersInGeneralAreaMap(l)) {
            ServerPlayer serverPlayerEntity;
            if (!(__player instanceof ServerPlayer) || !this.playerIsCloseEnoughForSpawning(serverPlayerEntity = (ServerPlayer)__player, pos)) continue;
            builder.add((Object)serverPlayerEntity);
        }
        return builder.build();
    }

    @Overwrite
    public boolean anyPlayerCloseEnoughForSpawning(ChunkPos pos) {
        long l = pos.pack();
        TriState triState = this.distanceManager.hasPlayersNearby(l);
        if (triState != TriState.DEFAULT) {
            return triState.toBoolean(true);
        }
        for (Object __player : this.areaPlayerChunkWatchingManager.getPlayersInGeneralAreaMap(l)) {
            ServerPlayer serverPlayerEntity;
            if (!(__player instanceof ServerPlayer) || !this.playerIsCloseEnoughForSpawning(serverPlayerEntity = (ServerPlayer)__player, pos)) continue;
            return true;
        }
        return false;
    }

    @Inject(method={"updatePlayerStatus"}, at={@At(value="HEAD")})
    private void onHandlePlayerAddedOrRemoved(ServerPlayer player, boolean added, CallbackInfo ci) {
        if (added) {
            this.vmp$updateWatchedSection(player);
            this.areaPlayerChunkWatchingManager.add(player, player.getLastSectionPos().chunk().pack());
        } else {
            this.areaPlayerChunkWatchingManager.remove(player);
        }
    }

    @Inject(method={"move"}, at={@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap;updatePlayerPos(Lnet/minecraft/server/level/ServerPlayer;)V")})
    private void onPlayerSectionChange(ServerPlayer player, CallbackInfo ci) {
        this.vmp$updateWatchedSection(player);
        this.areaPlayerChunkWatchingManager.movePlayer(player.getLastSectionPos().chunk().pack(), player);
    }

    @Unique
    private void vmp$updateWatchedSection(ServerPlayer player) {
        this.updatePlayerPos(player);
        player.connection.send((Packet)new ClientboundSetChunkCacheCenterPacket(player.getLastSectionPos().x(), player.getLastSectionPos().z()));
        player.setChunkTrackingView(ChunkTrackingView.of((ChunkPos)player.getLastSectionPos().chunk(), (int)this.getPlayerViewDistance(player)));
    }
}

