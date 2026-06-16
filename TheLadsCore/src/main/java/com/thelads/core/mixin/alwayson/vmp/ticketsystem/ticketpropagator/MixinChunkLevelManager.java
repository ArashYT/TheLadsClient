/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap
 *  it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.server.level.ChunkLevel
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.DistanceManager
 *  net.minecraft.server.level.LoadingChunkTracker
 *  net.minecraft.world.level.TicketStorage
 *  org.jetbrains.annotations.Nullable
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Mutable
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.ticketsystem.ticketpropagator;

import com.thelads.core.mixin.alwayson.vmp.access.IAbstractChunkHolder;
import com.thelads.core.mixin.alwayson.vmp.access.IChunkHolder;
import com.thelads.core.mixin.alwayson.vmp.access.IThreadedAnvilChunkStorage;
import io.papermc.paper.util.misc.Delayed8WayDistancePropagator2D;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.concurrent.Executor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.LoadingChunkTracker;
import net.minecraft.world.level.TicketStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={DistanceManager.class})
public abstract class MixinChunkLevelManager {
    @Mutable
    @Shadow
    @Final
    private LoadingChunkTracker loadingChunkTracker;
    @Shadow
    @Final
    private Executor mainThreadExecutor;
    @Unique
    protected Long2IntLinkedOpenHashMap ticketLevelUpdates;
    @Unique
    protected Delayed8WayDistancePropagator2D ticketLevelPropagator;
    @Unique
    private ObjectArrayFIFOQueue<ChunkHolder> pendingChunkHolderUpdates;

    @Shadow
    @Nullable
    protected abstract ChunkHolder getChunk(long var1);

    @Shadow
    @Nullable
    protected abstract ChunkHolder updateChunkScheduling(long var1, int var3, @Nullable ChunkHolder var4, int var5);

    @Unique
    private static int convertBetweenTicketLevels(int level) {
        return ChunkLevel.MAX_LEVEL - level + 1;
    }

    @Unique
    protected final void updateTicketLevel(long coordinate, int ticketLevel) {
        if (ticketLevel > ChunkLevel.MAX_LEVEL) {
            this.ticketLevelPropagator.removeSource(coordinate);
        } else {
            this.ticketLevelPropagator.setSource(coordinate, MixinChunkLevelManager.convertBetweenTicketLevels(ticketLevel));
        }
    }

    @Inject(method={"<init>"}, at={@At(value="RETURN")})
    private void onInit(TicketStorage ticketManager, Executor executor, Executor mainThreadExecutor, CallbackInfo ci) {
        this.loadingChunkTracker = null;
        this.ticketLevelUpdates = new Long2IntLinkedOpenHashMap(){

            protected void rehash(int newN) {
                if (newN < this.n) {
                    return;
                }
                super.rehash(newN);
            }
        };
        this.ticketLevelPropagator = new Delayed8WayDistancePropagator2D((coordinate, oldLevel, newLevel) -> this.ticketLevelUpdates.putAndMoveToLast(coordinate, MixinChunkLevelManager.convertBetweenTicketLevels(newLevel)));
        this.pendingChunkHolderUpdates = new ObjectArrayFIFOQueue();
        ticketManager.setLoadingChunkUpdatedListener((pos, level, added) -> this.updateTicketLevel(pos, level));
    }

    @Redirect(method={"runAllUpdates"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/LoadingChunkTracker;runDistanceUpdates(I)I"))
    public int tickTickets(LoadingChunkTracker instance, int distance, ChunkMap threadedAnvilChunkStorage) {
        if (!((IThreadedAnvilChunkStorage)threadedAnvilChunkStorage).getMainThreadExecutor().isSameThread()) {
            throw new ConcurrentModificationException("Attempted to tick tickets asynchronously");
        }
        boolean hasUpdates = this.ticketLevelPropagator.propagateUpdates();
        if (hasUpdates) {
            // empty if block
        }
        while (!this.ticketLevelUpdates.isEmpty()) {
            ChunkHolder holder;
            int currentLevel;
            hasUpdates = true;
            long key = this.ticketLevelUpdates.firstLongKey();
            int newLevel = this.ticketLevelUpdates.removeFirstInt();
            if (newLevel == (currentLevel = (holder = this.getChunk(key)) == null ? ChunkLevel.MAX_LEVEL + 1 : holder.getTicketLevel())) continue;
            if ((holder = this.updateChunkScheduling(key, newLevel, holder, currentLevel)) == null) {
                if (newLevel > ChunkLevel.MAX_LEVEL) continue;
                throw new IllegalStateException("Chunk holder not created");
            }
            this.pendingChunkHolderUpdates.enqueue(holder);
        }
        ArrayList<ChunkHolder> pending = new ArrayList<ChunkHolder>(this.pendingChunkHolderUpdates.size());
        while (!this.pendingChunkHolderUpdates.isEmpty()) {
            pending.add((ChunkHolder)this.pendingChunkHolderUpdates.dequeue());
        }
        this.pendingChunkHolderUpdates.clear();
        for (ChunkHolder element : pending) {
            ((IAbstractChunkHolder)element).invokeUpdateStatus(threadedAnvilChunkStorage);
        }
        for (ChunkHolder element : pending) {
            ((IChunkHolder)element).invokeUpdateFutures(threadedAnvilChunkStorage, this.mainThreadExecutor);
        }
        return hasUpdates ? distance - 1 : distance;
    }
}

