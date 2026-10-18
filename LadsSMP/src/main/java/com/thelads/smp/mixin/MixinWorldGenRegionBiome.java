package com.thelads.smp.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Biome lookups outside a world-gen region fall back to the biome source instead of throwing.
 * 26.3's WorldGenRegion.getChunk throws for chunks outside the generation step's dependencies even when not required, so
 * any feature code that reads the biome of a far position kills the chunk (More Mob Variants reads the skeleton's biome
 * in finalizeSpawn; Repurposed Structures' skeleton horsemen spawn there): "Requested chunk unavailable during world
 * generation", then the server thread hung (Arash-PC QA 2026-10-18 02:43, structure pass). The fallback is the same
 * noise biome the chunk will get, so nothing changes inside the region.
 */
@Mixin(WorldGenRegion.class)
public abstract class MixinWorldGenRegionBiome implements WorldGenLevel {
    @Shadow public abstract boolean hasChunk(int x, int z);
    @Shadow public abstract ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean require);
    @Shadow public abstract Holder<Biome> getUncachedNoiseBiome(int x, int y, int z);

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        int cx = QuartPos.toSection(x), cz = QuartPos.toSection(z);
        if (this.hasChunk(cx, cz)) {
            ChunkAccess chunk = this.getChunk(cx, cz, ChunkStatus.BIOMES, false);
            if (chunk != null) return chunk.getNoiseBiome(x, y, z);
        }
        return this.getUncachedNoiseBiome(x, y, z);
    }
}
