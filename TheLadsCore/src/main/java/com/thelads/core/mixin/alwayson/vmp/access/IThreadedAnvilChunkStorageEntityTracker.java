/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.SectionPos
 *  net.minecraft.server.level.ChunkMap$TrackedEntity
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={ChunkMap.TrackedEntity.class})
public interface IThreadedAnvilChunkStorageEntityTracker {
    @Accessor(value="entity")
    public Entity getEntity();

    @Accessor(value="lastSectionPos")
    public SectionPos getTrackedSection();

    @Accessor(value="lastSectionPos")
    public void setTrackedSection(SectionPos var1);

    @Invoker(value="getEffectiveRange")
    public int invokeGetMaxTrackDistance();
}

