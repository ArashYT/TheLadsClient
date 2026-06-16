/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.PlayerMap
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.util.thread.BlockableEventLoop
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={ChunkMap.class})
public interface IThreadedAnvilChunkStorage {
    @Invoker(value="getUpdatingChunkIfPresent")
    public ChunkHolder invokeGetCurrentChunkHolder(long var1);

    @Invoker(value="getVisibleChunkIfPresent")
    public ChunkHolder invokeGetChunkHolder(long var1);

    @Accessor(value="level")
    public ServerLevel getWorld();

    @Accessor(value="playerMap")
    public PlayerMap getPlayerChunkWatchingManager();

    @Accessor(value="mainThreadExecutor")
    public BlockableEventLoop<Runnable> getMainThreadExecutor();

    @Invoker(value="getPlayerViewDistance")
    public int invokeGetViewDistance(ServerPlayer var1);
}

