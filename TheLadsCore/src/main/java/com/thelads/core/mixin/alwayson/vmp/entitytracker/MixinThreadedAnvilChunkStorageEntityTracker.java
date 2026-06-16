/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ChunkMap$TrackedEntity
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.entitytracker;

import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorage;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={ChunkMap.TrackedEntity.class})
public abstract class MixinThreadedAnvilChunkStorageEntityTracker {
    @Shadow
    @Final
    private ChunkMap this$0;
    @Unique
    private int lastDistanceUpdate = 0;
    @Unique
    private int cachedMaxDistance = 0;

    @Shadow
    protected abstract int getEffectiveRange();

    @Redirect(method={"updatePlayer"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ChunkMap$TrackedEntity;getEffectiveRange()I"))
    private int redirectGetMaxTrackDistance(ChunkMap.TrackedEntity instance) {
        int ticks = ((IThreadedAnvilChunkStorage)this.this$0).getWorld().getServer().getTickCount();
        if (this.lastDistanceUpdate != ticks || this.cachedMaxDistance == 0) {
            int maxTrackDistance;
            this.cachedMaxDistance = maxTrackDistance = this.getEffectiveRange();
            this.lastDistanceUpdate = ticks;
            return maxTrackDistance;
        }
        return this.cachedMaxDistance;
    }
}

