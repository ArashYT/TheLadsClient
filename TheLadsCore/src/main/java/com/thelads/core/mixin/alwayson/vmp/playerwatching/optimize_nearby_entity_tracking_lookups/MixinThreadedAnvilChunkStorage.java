/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMaps
 *  it.unimi.dsi.fastutil.objects.ObjectCollection
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ChunkMap$DistanceManager
 *  net.minecraft.server.level.ChunkMap$TrackedEntity
 *  net.minecraft.server.level.PlayerMap
 *  net.minecraft.server.level.ServerPlayer
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_entity_tracking_lookups;

import com.thelads.core.features.alwayson.vmp.common.playerwatching.NearbyEntityTracking;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import java.util.List;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={ChunkMap.class})
public class MixinThreadedAnvilChunkStorage {
    @Shadow
    @Final
    private Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;
    @Shadow
    @Final
    private ChunkMap.DistanceManager distanceManager;
    @Shadow
    @Final
    private PlayerMap playerMap;
    @Unique
    private final NearbyEntityTracking nearbyEntityTracking = new NearbyEntityTracking();

    @Redirect(method={"addEntity"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap$TrackedEntity;updatePlayers(Ljava/util/List;)V"))
    private void redirectUpdateOnAddEntity(ChunkMap.TrackedEntity instance, List<ServerPlayer> players) {
        this.nearbyEntityTracking.addEntityTracker(instance);
    }

    @Redirect(method={"addEntity"}, at=@At(value="INVOKE", target="Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"))
    private <T> ObjectCollection<T> nullifyTrackerListOnAddEntity(Int2ObjectMap<T> instance) {
        if (this.entityMap == instance) {
            return Int2ObjectMaps.<T>emptyMap().values();
        }
        return instance.values();
    }

    @Redirect(method={"removeEntity"}, at=@At(value="INVOKE", target="Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"))
    private <T> ObjectCollection<T> nullifyTrackerListOnRemoveEntity(Int2ObjectMap<T> instance) {
        if (this.entityMap == instance) {
            return Int2ObjectMaps.<T>emptyMap().values();
        }
        return instance.values();
    }

    @Redirect(method={"removeEntity"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap$TrackedEntity;broadcastRemoved()V"))
    private void redirectUpdateOnRemoveEntity(ChunkMap.TrackedEntity instance) {
        this.nearbyEntityTracking.removeEntityTracker(instance);
        instance.broadcastRemoved();
    }

    @Redirect(method={"move"}, at=@At(value="INVOKE", target="Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"))
    private <T> ObjectCollection<T> redirectTrackersOnUpdatePosition(Int2ObjectMap<T> instance, ServerPlayer player) {
        if (this.entityMap != instance) {
            return instance.values();
        }
        return Int2ObjectMaps.<T>emptyMap().values();
    }

    @Overwrite
    public void tick() {
        try {
            this.nearbyEntityTracking.tick(this.distanceManager);
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

