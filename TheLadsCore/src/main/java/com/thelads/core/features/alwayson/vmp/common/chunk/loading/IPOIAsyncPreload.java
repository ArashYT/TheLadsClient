/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerLevel
 */
package com.thelads.core.features.alwayson.vmp.common.chunk.loading;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface IPOIAsyncPreload {
    public CompletableFuture<Void> preloadChunksAtAsync(ServerLevel var1, BlockPos var2, int var3);
}

