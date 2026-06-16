/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
 *  it.unimi.dsi.fastutil.objects.ObjectListIterator
 *  it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap
 *  it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap
 *  it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet
 *  net.fabricmc.loader.api.FabricLoader
 *  net.minecraft.core.SectionPos
 *  net.minecraft.server.level.ChunkMap$DistanceManager
 *  net.minecraft.server.level.ChunkMap$TrackedEntity
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.entity.EntityAccess
 *  net.minecraft.world.phys.Vec3
 */
package com.thelads.core.features.alwayson.vmp.common.playerwatching;

import com.thelads.core.features.alwayson.vmp.common.config.Config;
import com.thelads.core.features.alwayson.vmp.common.maps.AreaMap;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.EntityTrackerExtension;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.ServerPlayerEntityExtension;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.compat.EntityPositionTransformer;
import com.thelads.core.features.alwayson.vmp.common.util.SimpleObjectPool;
import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorage;
import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorageEntityTracker;
import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorageLevelManager;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.Vec3;

public class NearbyEntityTracking {
    private static final EntityPositionTransformer[] transformers;
    private final SimpleObjectPool<ReferenceLinkedOpenHashSet<?>> pooledHashSets = new SimpleObjectPool<>(unused -> new ReferenceLinkedOpenHashSet<>(), ReferenceLinkedOpenHashSet::clear, ts -> {
        ts.clear();
        ts.trim(4);
    }, 8192);
    private final AreaMap<ChunkMap.TrackedEntity> areaMap = new AreaMap();
    private final Reference2ReferenceLinkedOpenHashMap<ServerPlayer, ReferenceLinkedOpenHashSet<ChunkMap.TrackedEntity>> playerTrackers = new Reference2ReferenceLinkedOpenHashMap();
    private final Reference2LongOpenHashMap<ChunkMap.TrackedEntity> tracker2ChunkPos = new Reference2LongOpenHashMap();
    private ChunkMap.DistanceManager ticketManager;
    private static final int STAGING_TRACKER_LIFETIME = 200;
    private final AtomicLong ticks = new AtomicLong(0L);
    private final ObjectLinkedOpenHashSet<StagedTracker> stagingTrackers = new ObjectLinkedOpenHashSet();
    private final ReferenceLinkedOpenHashSet<ChunkMap.TrackedEntity> trackerTickList = new ReferenceLinkedOpenHashSet<ChunkMap.TrackedEntity>(){

        protected void rehash(int newN) {
            if (this.n < newN) {
                super.rehash(newN);
            }
        }
    };

    public static void init() {
    }

    private void addEntityTrackerAreaMap(ChunkMap.TrackedEntity tracker) {
        ChunkPos pos = NearbyEntityTracking.getEntityChunkPos(((IThreadedAnvilChunkStorageEntityTracker)tracker).getEntity());
        this.areaMap.add(tracker, pos.x(), pos.z(), this.getChunkViewDistance(tracker));
        this.tracker2ChunkPos.put(tracker, pos.pack());
    }

    public void addEntityTracker(ChunkMap.TrackedEntity tracker) {
        Entity entity = ((IThreadedAnvilChunkStorageEntityTracker)tracker).getEntity();
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)entity;
            this.addPlayer(player);
        }
        if (Config.OPTIMIZED_ENTITY_TRACKING_USE_STAGING_AREA) {
            this.stagingTrackers.addAndMoveToLast(new StagedTracker(tracker, this.ticks.get()));
            for (ServerPlayer player : this.playerTrackers.keySet()) {
                tracker.updatePlayer(player);
            }
        } else {
            this.addEntityTrackerAreaMap(tracker);
        }
    }

    public void removeEntityTracker(ChunkMap.TrackedEntity tracker) {
        Entity entity = ((IThreadedAnvilChunkStorageEntityTracker)tracker).getEntity();
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)entity;
            this.removePlayer(player);
        }
        if (this.stagingTrackers.remove((Object)new StagedTracker(tracker, 0L))) {
            tracker.broadcastRemoved();
        }
        this.areaMap.remove(tracker);
        this.tracker2ChunkPos.removeLong((Object)tracker);
        if (((IThreadedAnvilChunkStorageEntityTracker)tracker).getEntity() instanceof ServerPlayer && this.ticketManager != null) {
            this.tick0();
        }
    }

    public void addPlayer(ServerPlayer player) {
        this.playerTrackers.put(player, (ReferenceLinkedOpenHashSet<ChunkMap.TrackedEntity>) (ReferenceLinkedOpenHashSet) this.pooledHashSets.alloc());
    }

    public void removePlayer(ServerPlayer player) {
        for (StagedTracker stagingTracker : this.stagingTrackers) {
            stagingTracker.tracker().removePlayer(player);
        }
        ReferenceLinkedOpenHashSet<ChunkMap.TrackedEntity> originalTrackers = (ReferenceLinkedOpenHashSet<ChunkMap.TrackedEntity>)this.playerTrackers.remove(player);
        if (originalTrackers != null) {
            for (ChunkMap.TrackedEntity tracker : originalTrackers) {
                tracker.removePlayer(player);
            }
            this.pooledHashSets.release(originalTrackers);
        }
    }

    private static ChunkPos getEntityChunkPos(Entity entity) {
        Vec3 pos = entity.position();
        for (EntityPositionTransformer transformer : transformers) {
            pos = transformer.transform(entity, pos);
        }
        return new ChunkPos(SectionPos.posToSectionCoord((double)pos.x), SectionPos.posToSectionCoord((double)pos.z));
    }

    public void tick(ChunkMap.DistanceManager ticketManager) {
        this.ticketManager = ticketManager;
        this.tick0();
    }

    private void tick0() {
        this.tickStaging();
        for (var entry : this.tracker2ChunkPos.reference2LongEntrySet()) {
            ChunkPos pos = NearbyEntityTracking.getEntityChunkPos(((IThreadedAnvilChunkStorageEntityTracker)entry.getKey()).getEntity());
            if (pos.pack() == entry.getLongValue()) continue;
            this.areaMap.update((ChunkMap.TrackedEntity)entry.getKey(), pos.x(), pos.z(), this.getChunkViewDistance((ChunkMap.TrackedEntity)entry.getKey()));
            entry.setValue(pos.pack());
        }
        this.trackerTickList.clear();
        for (var entry : this.playerTrackers.entrySet()) {
            ServerPlayer player = (ServerPlayer)entry.getKey();
            Set<ChunkMap.TrackedEntity> currentTrackers = this.areaMap.getObjectsInRange(NearbyEntityTracking.getEntityChunkPos((Entity)player).pack());
            boolean isPlayerPositionUpdated = ((ServerPlayerEntityExtension)player).vmpTracking$isPositionUpdated();
            ((ServerPlayerEntityExtension)player).vmpTracking$updatePosition();
            ReferenceLinkedOpenHashSet<ChunkMap.TrackedEntity> trackers = entry.getValue();
            ObjectListIterator<ChunkMap.TrackedEntity> iterator = trackers.iterator();
            while (iterator.hasNext()) {
                ChunkMap.TrackedEntity entityTracker = iterator.next();
                if (currentTrackers.contains(entityTracker)) {
                    this.handleTracker(player, isPlayerPositionUpdated, entityTracker);
                    continue;
                }
                entityTracker.removePlayer(player);
                iterator.remove();
            }
            for (ChunkMap.TrackedEntity entityTracker : currentTrackers) {
                if (trackers.contains(entityTracker)) continue;
                this.handleTracker(player, isPlayerPositionUpdated, entityTracker);
                trackers.add(entityTracker);
            }
        }
        for (ChunkMap.TrackedEntity entityTracker : this.trackerTickList) {
            ((EntityTrackerExtension)entityTracker).updatePosition();
        }
    }

    private void tickStaging() {
        StagedTracker stagingTracker;
        long currentTicks = this.ticks.incrementAndGet();
        ObjectListIterator iterator = this.stagingTrackers.iterator();
        while (iterator.hasNext() && currentTicks - (stagingTracker = (StagedTracker)iterator.next()).tickAdded() >= 200L) {
            iterator.remove();
            this.addEntityTrackerAreaMap(stagingTracker.tracker());
        }
        List players = ((IThreadedAnvilChunkStorage)((IThreadedAnvilChunkStorageLevelManager)this.ticketManager).getField_17443()).getWorld().players();
        for (StagedTracker staged : this.stagingTrackers) {
            Entity entity;
            SectionPos chunkSectionPos2;
            boolean bl;
            ChunkMap.TrackedEntity entityTracker = staged.tracker();
            SectionPos chunkSectionPos = ((IThreadedAnvilChunkStorageEntityTracker)entityTracker).getTrackedSection();
            boolean bl2 = bl = !Objects.equals(chunkSectionPos, chunkSectionPos2 = SectionPos.of((EntityAccess)(entity = ((IThreadedAnvilChunkStorageEntityTracker)entityTracker).getEntity())));
            if (bl) {
                entityTracker.updatePlayers(players);
                ((IThreadedAnvilChunkStorageEntityTracker)entityTracker).setTrackedSection(chunkSectionPos2);
            }
            if (!bl && !this.ticketManager.inEntityTickingRange(chunkSectionPos2.chunk().pack())) continue;
            ((EntityTrackerExtension)entityTracker).tryTick();
        }
        for (StagedTracker staged : this.stagingTrackers) {
            staged.tracker().updatePlayers(players);
        }
    }

    private void handleTracker(ServerPlayer player, boolean isPlayerPositionUpdated, ChunkMap.TrackedEntity entityTracker) {
        SectionPos trackedPos = ((IThreadedAnvilChunkStorageEntityTracker)entityTracker).getTrackedSection();
        if (this.trackerTickList.add(entityTracker) && this.ticketManager.inEntityTickingRange(ChunkPos.pack((int)trackedPos.x(), (int)trackedPos.z()))) {
            NearbyEntityTracking.tryTickTracker(entityTracker);
        }
        if (isPlayerPositionUpdated || ((EntityTrackerExtension)entityTracker).isPositionUpdated()) {
            NearbyEntityTracking.tryUpdateTracker(entityTracker, player);
        }
    }

    private static void tryUpdateTracker(ChunkMap.TrackedEntity entityTracker, ServerPlayer player) {
        entityTracker.updatePlayer(player);
    }

    private static void tryTickTracker(ChunkMap.TrackedEntity entityTracker) {
        ((EntityTrackerExtension)entityTracker).tryTick();
    }

    private int getChunkViewDistance(ChunkMap.TrackedEntity tracker) {
        return (int)Math.ceil((double)((IThreadedAnvilChunkStorageEntityTracker)tracker).invokeGetMaxTrackDistance() / 16.0) + 1;
    }

    static {
        ArrayList<EntityPositionTransformer> list = new ArrayList<EntityPositionTransformer>();
        if (FabricLoader.getInstance().isModLoaded("valkyrienskies")) {
            System.out.println("ValkyrienSkies detected, applying compatibility patch");
            try {
                list.add((EntityPositionTransformer)Class.forName("com.thelads.core.features.alwayson.vmp.common.playerwatching.compat.ValkyrienSkies2ShipPositionTransformer").getDeclaredConstructor(new Class[0]).newInstance(new Object[0]));
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
        transformers = (EntityPositionTransformer[])list.toArray(EntityPositionTransformer[]::new);
    }

    private record StagedTracker(ChunkMap.TrackedEntity tracker, long tickAdded) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            StagedTracker that = (StagedTracker)o;
            return this.tracker == that.tracker;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.tracker);
        }
    }
}

