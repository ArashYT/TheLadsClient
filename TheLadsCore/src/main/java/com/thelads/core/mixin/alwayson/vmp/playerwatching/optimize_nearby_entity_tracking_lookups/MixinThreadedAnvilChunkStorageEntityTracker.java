/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.SectionPos
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ChunkMap$TrackedEntity
 *  net.minecraft.server.level.ServerEntity
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.network.ServerPlayerConnection
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.entity.EntityAccess
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.At$Shift
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_entity_tracking_lookups;

import com.thelads.core.features.alwayson.vmp.common.playerwatching.EntityTrackerEntryExtension;
import com.thelads.core.features.alwayson.vmp.common.playerwatching.EntityTrackerExtension;
import java.util.Set;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ChunkMap.TrackedEntity.class})
public abstract class MixinThreadedAnvilChunkStorageEntityTracker
implements EntityTrackerExtension {
    @Shadow
    @Final
    private Entity entity;
    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;
    @Shadow
    @Final
    private ServerEntity serverEntity;
    @Shadow
    private SectionPos lastSectionPos;
    @Unique
    private double prevX = Double.NaN;
    @Unique
    private double prevY = Double.NaN;
    @Unique
    private double prevZ = Double.NaN;

    @Shadow
    public abstract void updatePlayer(ServerPlayer var1);

    @Override
    public boolean isPositionUpdated() {
        Vec3 pos = this.entity.position();
        return pos.x != this.prevX || pos.y != this.prevY || pos.z != this.prevZ;
    }

    @Override
    public void updatePosition() {
        Vec3 pos = this.entity.position();
        this.prevX = pos.x;
        this.prevY = pos.y;
        this.prevZ = pos.z;
        this.lastSectionPos = SectionPos.of((EntityAccess)this.entity);
    }

    @Override
    public Vec3 getPreviousLocation() {
        return new Vec3(this.prevX, this.prevY, this.prevZ);
    }

    @Override
    public long getPreviousChunkPos() {
        return ChunkPos.pack((int)SectionPos.blockToSectionCoord((int)((int)this.prevX)), (int)SectionPos.blockToSectionCoord((int)((int)this.prevX)));
    }

    @Override
    public void updateListeners(Set<ServerPlayer> triedPlayers) {
        for (ServerPlayerConnection listener : (ServerPlayerConnection[])this.seenBy.toArray(ServerPlayerConnection[]::new)) {
            ServerPlayer player = listener.getPlayer();
            if (triedPlayers != null) {
                triedPlayers.add(player);
            }
            if (player == null) continue;
            this.updatePlayer(player);
        }
    }

    @Override
    public void tryTick() {
        this.lastSectionPos = SectionPos.of((EntityAccess)this.entity);
        this.serverEntity.sendChanges();
    }

    @Inject(method={"updatePlayer"}, at={@At(value="INVOKE", target="Ljava/util/Set;add(Ljava/lang/Object;)Z", shift=At.Shift.BEFORE)})
    private void beforeStartTracking(ServerPlayer player, CallbackInfo ci) {
        if (this.seenBy.isEmpty()) {
            ((EntityTrackerEntryExtension)this.serverEntity).vmp$tickAlways();
        }
    }

    @Redirect(method={"updatePlayer"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"))
    private boolean assumeAlwaysTracked(ChunkMap instance, ServerPlayer player, int chunkX, int chunkZ) {
        return true;
    }
}

