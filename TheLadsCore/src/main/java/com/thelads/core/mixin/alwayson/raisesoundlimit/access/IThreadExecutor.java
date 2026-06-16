/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.util.thread.BlockableEventLoop
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package com.thelads.core.mixin.alwayson.raisesoundlimit.access;

import java.util.Queue;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={BlockableEventLoop.class})
public interface IThreadExecutor<R> {
    @Accessor(value="pendingRunnables")
    public Queue<R> getTasks();

    @Accessor(value="blockingCount")
    public int getExecutionsInProgress();
}

