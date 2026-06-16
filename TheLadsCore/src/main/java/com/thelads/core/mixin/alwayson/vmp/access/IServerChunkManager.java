/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerChunkCache
 *  net.minecraft.world.level.TicketStorage
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={ServerChunkCache.class})
public interface IServerChunkManager {
    @Accessor(value="ticketStorage")
    public TicketStorage getTicketManager();

    @Invoker(value="runDistanceManagerUpdates")
    public boolean invokeUpdateChunks();
}

