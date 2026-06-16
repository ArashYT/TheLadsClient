/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkHolder
 *  net.minecraft.server.level.ChunkMap
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Invoker
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import java.util.concurrent.Executor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={ChunkHolder.class})
public interface IChunkHolder {
    @Invoker(value="updateFutures")
    public void invokeUpdateFutures(ChunkMap var1, Executor var2);
}

