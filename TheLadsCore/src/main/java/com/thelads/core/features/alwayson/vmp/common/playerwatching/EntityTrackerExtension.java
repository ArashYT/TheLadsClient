/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.phys.Vec3
 */
package com.thelads.core.features.alwayson.vmp.common.playerwatching;

import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public interface EntityTrackerExtension {
    public boolean isPositionUpdated();

    public void updatePosition();

    public Vec3 getPreviousLocation();

    public long getPreviousChunkPos();

    public void updateListeners(Set<ServerPlayer> var1);

    public void tryTick();
}

