package com.thelads.smp.features.arise.nb6;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Biome and environment groups for Tide-style regional fish distribution.
 */
public enum FishingBiomeGroup {
    FRESHWATER,
    COLD,
    WARM_OCEAN,
    SWAMP,
    CAVE,
    NETHER,
    THE_END,
    NIGHT,
    STORM;

    public static FishingBiomeGroup evaluate(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return FRESHWATER;
        }

        // 1. Dimension checks
        ResourceKey<Level> dim = level.dimension();
        if (dim == Level.NETHER) {
            return NETHER;
        }
        if (dim == Level.END) {
            return THE_END;
        }

        // 2. Underground depth check
        if (pos.getY() < 0) {
            return CAVE;
        }

        // 3. Weather check (rain or thunderstorm with sky access)
        if (level.isRaining() && level.canSeeSky(pos.above())) {
            // 35% chance to bias toward storm fish pool in rain
            if (level.getRandom().nextFloat() < 0.35f) {
                return STORM;
            }
        }

        // 4. Night check
        long dayTime = level.getOverworldClockTime() % 24000L;
        if (dayTime >= 13000L && dayTime <= 23000L) {
            // 35% chance to bias toward nocturnal fish pool
            if (level.getRandom().nextFloat() < 0.35f) {
                return NIGHT;
            }
        }

        // 5. Biome categorization
        Holder<Biome> biomeHolder = level.getBiome(pos);
        if (biomeHolder.isBound()) {
            ResourceKey<Biome> key = biomeHolder.unwrapKey().orElse(null);
            if (key != null) {
                String path = key.identifier().getPath().toLowerCase();
                if (path.contains("swamp") || path.contains("mangrove")) {
                    return SWAMP;
                }
                if (path.contains("frozen") || path.contains("snow") || path.contains("ice") || path.contains("cold")) {
                    return COLD;
                }
                if (path.contains("warm") || path.contains("coral") || path.contains("lukewarm") || path.contains("beach")) {
                    return WARM_OCEAN;
                }
                if (path.contains("cave") || path.contains("deep_dark")) {
                    return CAVE;
                }
            }
        }

        return FRESHWATER;
    }
}
