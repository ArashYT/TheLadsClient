package com.thelads.smp.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** "Is this block's chunk fully loaded right now", without ever waiting for it: Level.hasChunkAt is also true for a chunk that is only being generated. */
public final class LoadedChunks {
    private LoadedChunks() { }

    public static boolean isLoaded(Level level, BlockPos pos) {
        if (level instanceof ServerLevel sl) return sl.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
        return level.hasChunkAt(pos);
    }
}
