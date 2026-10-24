package com.thelads.core.client;

import com.thelads.core.modules.HideFoliageModule;

/**
 * Hide Foliage's blocks, by exact id (never a part of a name: "grass" must not catch grass_block). Each version looks up the ids its
 * registry has ("grass" is short grass up to 1.20.2, "short_grass" after) and skips the rest; 1.8.9 maps its tallgrass, deadbush and
 * double_plant variants to the same groups. The chunk builder asks per block, so versions keep the result of {@link #mask} as a
 * plain array of blocks and rebuild their chunk meshes when the mask changes.
 */
public final class HideFoliage {
    public static final int GRASS = 0, BUSHES = 1, SEAGRASS = 2, NETHER = 3, LEAF_LITTER = 4, TALL = 5;
    /** Tall Plants' gate name in HypixelSafetyManager's risky set: a crouching player hides in them. */
    public static final String TALL_GATE = "HideTallFoliage";
    /** Block ids (namespace minecraft) per group, indexed by the constants above. */
    public static final String[][] IDS = {
        {"grass", "short_grass", "fern", "short_dry_grass"},
        {"dead_bush", "bush", "firefly_bush"},
        {"seagrass"},
        {"nether_sprouts", "crimson_roots", "warped_roots"},
        {"leaf_litter"},
        {"tall_grass", "large_fern", "tall_dry_grass", "tall_seagrass"}};

    private HideFoliage() {}

    /** The groups hidden now, one bit each (0 with the module off); Tall Plants only where the server rules allow them. */
    public static int mask(HideFoliageModule m) {
        if (m == null || !m.isEnabled()) return 0;
        int mask = bit(m.grass.get(), GRASS) | bit(m.bushes.get(), BUSHES) | bit(m.seagrass.get(), SEAGRASS)
            | bit(m.nether.get(), NETHER) | bit(m.leafLitter.get(), LEAF_LITTER);
        if (m.tall.get() && !HypixelSafetyManager.isDisallowed(TALL_GATE)) mask |= 1 << TALL;
        return mask;
    }

    public static boolean hides(int mask, int group) {
        return (mask & (1 << group)) != 0;
    }

    private static int bit(boolean on, int group) {
        return on ? 1 << group : 0;
    }
}
