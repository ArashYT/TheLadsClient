/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2LongMap$Entry
 *  it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
 *  it.unimi.dsi.fastutil.objects.ObjectIterator
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ChunkTrackingView
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.MobCategory
 *  net.minecraft.world.level.ChunkPos
 */
package com.thelads.core.features.alwayson.vmp.common.chunkwatching;

import com.thelads.core.features.alwayson.vmp.common.chunkwatching.PlayerClientVDTracking;
import com.thelads.core.features.alwayson.vmp.common.maps.AreaMap;
import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorage;
import io.papermc.paper.util.MCUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

public class AreaPlayerChunkWatchingManager {
    public static final int GENERAL_PLAYER_AREA_MAP_DISTANCE = (int)Math.ceil((double)Arrays.stream(MobCategory.values()).mapToInt(MobCategory::getDespawnDistance).reduce(0, Math::max) / 16.0);
    private final AreaMap<ServerPlayer> playerAreaMap;
    private final AreaMap<ServerPlayer> generalPlayerAreaMap = new AreaMap();
    private final Object2LongOpenHashMap<ServerPlayer> positions = new Object2LongOpenHashMap();
    private final ChunkMap tacs;
    private Listener addListener = null;
    private Listener removeListener = null;

    public AreaPlayerChunkWatchingManager() {
        this(null, null, null);
    }

    public AreaPlayerChunkWatchingManager(Listener addListener, Listener removeListener, ChunkMap tacs) {
        this.addListener = addListener;
        this.removeListener = removeListener;
        this.tacs = Objects.requireNonNull(tacs);
        this.playerAreaMap = new AreaMap<ServerPlayer>((object, x, z) -> {
            if (this.addListener != null) {
                this.addListener.accept((ServerPlayer)object, x, z);
            }
        }, (object, x, z) -> {
            if (this.removeListener != null) {
                this.removeListener.accept((ServerPlayer)object, x, z);
            }
        }, true);
    }

    public void tick() {
        for (Object2LongMap.Entry entry : this.positions.object2LongEntrySet()) {
            ServerPlayer player = (ServerPlayer)entry.getKey();
            PlayerClientVDTracking vdTracking = (PlayerClientVDTracking)player;
            if (!vdTracking.isClientViewDistanceChanged()) continue;
            vdTracking.getClientViewDistance();
            long pos = entry.getLongValue();
            player.setChunkTrackingView(ChunkTrackingView.of((ChunkPos)ChunkPos.unpack((long)pos), (int)this.getViewDistance(player)));
            this.movePlayer(pos, player);
        }
    }

    public void onWatchDistanceChange() {
        ObjectIterator iterator = this.positions.object2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Object2LongMap.Entry entry = (Object2LongMap.Entry)iterator.next();
            ((ServerPlayer)entry.getKey()).setChunkTrackingView(ChunkTrackingView.of((ChunkPos)ChunkPos.unpack((long)entry.getLongValue()), (int)this.getViewDistance((ServerPlayer)entry.getKey())));
            this.playerAreaMap.update((ServerPlayer)entry.getKey(), MCUtil.getCoordinateX(entry.getLongValue()), MCUtil.getCoordinateZ(entry.getLongValue()), this.getViewDistance((ServerPlayer)entry.getKey()));
            this.generalPlayerAreaMap.update((ServerPlayer)entry.getKey(), MCUtil.getCoordinateX(entry.getLongValue()), MCUtil.getCoordinateZ(entry.getLongValue()), GENERAL_PLAYER_AREA_MAP_DISTANCE);
        }
    }

    public Set<ServerPlayer> getPlayersWatchingChunk(long l) {
        return this.playerAreaMap.getObjectsInRange(l);
    }

    public Object[] getPlayersWatchingChunkArray(long coordinateKey) {
        return this.playerAreaMap.getObjectsInRangeArray(coordinateKey);
    }

    public Object[] getPlayersInGeneralAreaMap(long coordinateKey) {
        return this.generalPlayerAreaMap.getObjectsInRangeArray(coordinateKey);
    }

    public void add(ServerPlayer player, long pos) {
        int x = ChunkPos.getX((long)pos);
        int z = ChunkPos.getZ((long)pos);
        this.playerAreaMap.add(player, x, z, this.getViewDistance(player));
        this.generalPlayerAreaMap.add(player, x, z, GENERAL_PLAYER_AREA_MAP_DISTANCE);
        this.positions.put(player, MCUtil.getCoordinateKey(x, z));
    }

    public void remove(ServerPlayer player) {
        this.playerAreaMap.remove(player);
        this.generalPlayerAreaMap.remove(player);
        this.positions.removeLong(player);
    }

    public void movePlayer(long currentPos, ServerPlayer player) {
        int x = ChunkPos.getX((long)currentPos);
        int z = ChunkPos.getZ((long)currentPos);
        this.playerAreaMap.update(player, x, z, this.getViewDistance(player));
        this.generalPlayerAreaMap.update(player, x, z, GENERAL_PLAYER_AREA_MAP_DISTANCE);
        this.positions.put(player, MCUtil.getCoordinateKey(x, z));
    }

    private int getViewDistance(ServerPlayer player) {
        return ((IThreadedAnvilChunkStorage)this.tacs).invokeGetViewDistance(player) + 1;
    }

    public static interface Listener {
        public void accept(ServerPlayer var1, int var2, int var3);
    }
}

