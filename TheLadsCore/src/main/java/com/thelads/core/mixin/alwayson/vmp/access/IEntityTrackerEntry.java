/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.protocol.game.VecDeltaCodec
 *  net.minecraft.server.level.ServerEntity
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import java.util.List;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={ServerEntity.class})
public interface IEntityTrackerEntry {
    @Invoker(value="sendDirtyEntityData")
    public void invokeSyncEntityData();

    @Accessor(value="entity")
    public Entity getEntity();

    @Accessor(value="lastPassengers")
    public List<Entity> getLastPassengers();

    @Accessor(value="lastPassengers")
    public void setLastPassengers(List<Entity> var1);

    @Accessor(value="positionCodec")
    public VecDeltaCodec getTrackedPos();
}

