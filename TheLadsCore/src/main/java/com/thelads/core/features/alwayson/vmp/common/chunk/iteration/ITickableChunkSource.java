/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkHolder
 */
package com.thelads.core.features.alwayson.vmp.common.chunk.iteration;

import net.minecraft.server.level.ChunkHolder;

public interface ITickableChunkSource {
    public Iterable<ChunkHolder> vmp$tickableChunksIterator();
}

