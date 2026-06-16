/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerEntity
 *  net.minecraft.util.Mth
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching.optimize_nearby_entity_tracking_lookups;

import com.thelads.core.features.alwayson.vmp.common.playerwatching.EntityTrackerEntryExtension;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={ServerEntity.class})
public abstract class MixinEntityTrackerEntry
implements EntityTrackerEntryExtension {
    @Shadow
    private int tickCount;
    @Shadow
    @Final
    private int updateInterval;
    @Shadow
    @Final
    private Entity entity;
    @Shadow
    private int teleportDelay;

    @Shadow
    public abstract void sendChanges();

    @Shadow
    protected abstract void sendDirtyEntityData();

    @Override
    public void vmp$tickAlways() {
        this.tickCount = Mth.roundToward((int)this.tickCount, (int)this.updateInterval);
        this.teleportDelay = 65536;
        this.entity.needsSync = true;
        this.sendChanges();
    }

    @Override
    public void vmp$syncEntityData() {
        ++this.tickCount;
        if (this.tickCount % this.updateInterval == 0) {
            this.sendDirtyEntityData();
        }
    }
}

