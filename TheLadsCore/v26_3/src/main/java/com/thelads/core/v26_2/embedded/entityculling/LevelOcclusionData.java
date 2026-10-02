package com.thelads.core.v26_2.embedded.entityculling;

import com.thelads.core.v26_2.embedded.entityculling.occlusion.DataProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Worker-thread view of the client world for the ray caster: only full opaque cubes block sight. */
final class LevelOcclusionData implements DataProvider {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private LevelChunk chunk;
    private int chunkX, chunkZ;
    private boolean cached;

    void level(ClientLevel level) {
        this.level = level;
        cached = false;
    }

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        if (!cached || chunkX != this.chunkX || chunkZ != this.chunkZ) {
            chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            cached = true;
        }
        // An unloaded chunk counts as open space, never as a wall.
        return true;
    }

    @Override
    public boolean isOpaqueFullCube(int x, int y, int z) {
        LevelChunk current = chunk;
        return current != null && current.getBlockState(pos.set(x, y, z)).isSolidRender();
    }
}
