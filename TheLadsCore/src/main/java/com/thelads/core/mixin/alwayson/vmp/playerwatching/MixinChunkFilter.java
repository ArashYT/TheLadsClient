/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkTrackingView
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching;

import net.minecraft.server.level.ChunkTrackingView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value={ChunkTrackingView.class})
public interface MixinChunkFilter {
    @Overwrite
    public static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeEdge) {
        int actualViewDistance = viewDistance + (includeEdge ? 1 : 0);
        int xDistance = Math.abs(centerX - x);
        int zDistance = Math.abs(centerZ - z);
        return xDistance <= actualViewDistance && zDistance <= actualViewDistance;
    }
}

