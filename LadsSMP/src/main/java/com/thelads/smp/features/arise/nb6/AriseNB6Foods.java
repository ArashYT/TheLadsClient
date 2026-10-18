package com.thelads.smp.features.arise.nb6;

import net.minecraft.world.food.FoodProperties;

/**
 * Clean-room food properties for 20 custom fish species and their cooked variants.
 */
public final class AriseNB6Foods {
    private AriseNB6Foods() {}

    private static FoodProperties raw(int nutrition, float saturation) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
    }

    private static FoodProperties cooked(int nutrition, float saturation) {
        return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
    }

    // 1. Freshwater / Rivers & Lakes
    public static final FoodProperties BASS = raw(2, 0.3f);
    public static final FoodProperties COOKED_BASS = cooked(6, 0.8f);

    public static final FoodProperties BLUEGILL = raw(2, 0.3f);
    public static final FoodProperties COOKED_BLUEGILL = cooked(5, 0.7f);

    public static final FoodProperties CARP = raw(3, 0.3f);
    public static final FoodProperties COOKED_CARP = cooked(7, 0.8f);

    public static final FoodProperties TROUT = raw(3, 0.35f);
    public static final FoodProperties COOKED_TROUT = cooked(7, 0.8f);

    // 2. Cold & Frozen Oceans / Snowy Rivers
    public static final FoodProperties GLACIER_COD = raw(2, 0.3f);
    public static final FoodProperties COOKED_GLACIER_COD = cooked(6, 0.75f);

    public static final FoodProperties FROSTBITE_FLOUNDER = raw(3, 0.35f);
    public static final FoodProperties COOKED_FROSTBITE_FLOUNDER = cooked(7, 0.8f);

    // 3. Warm & Deep Oceans / Coral Reefs
    public static final FoodProperties TUNA = raw(3, 0.4f);
    public static final FoodProperties COOKED_TUNA = cooked(8, 0.85f);

    public static final FoodProperties SNAPPER = raw(2, 0.3f);
    public static final FoodProperties COOKED_SNAPPER = cooked(6, 0.75f);

    public static final FoodProperties SAILFISH = raw(4, 0.4f);
    public static final FoodProperties COOKED_SAILFISH = cooked(9, 0.9f);

    // 4. Swamps & Mangroves
    public static final FoodProperties CATFISH = raw(2, 0.3f);
    public static final FoodProperties COOKED_CATFISH = cooked(6, 0.75f);

    public static final FoodProperties BULLHEAD = raw(3, 0.35f);
    public static final FoodProperties COOKED_BULLHEAD = cooked(7, 0.8f);

    // 5. Caves & Deep Underground (below Y=0)
    public static final FoodProperties ABYSSAL_ANGLER = raw(3, 0.4f);
    public static final FoodProperties COOKED_ABYSSAL_ANGLER = cooked(8, 0.85f);

    public static final FoodProperties CAVE_TETRA = raw(2, 0.3f);
    public static final FoodProperties COOKED_CAVE_TETRA = cooked(5, 0.65f);

    // 6. Night & Storm Specialties
    public static final FoodProperties LUNAR_GUPPY = raw(2, 0.3f);
    public static final FoodProperties COOKED_LUNAR_GUPPY = cooked(6, 0.7f);

    public static final FoodProperties STORM_PERCH = raw(3, 0.35f);
    public static final FoodProperties COOKED_STORM_PERCH = cooked(7, 0.8f);

    // 7. Nether Wastes & Forests (Lava / Nether)
    public static final FoodProperties MAGMA_MACKEREL = raw(3, 0.4f);
    public static final FoodProperties COOKED_MAGMA_MACKEREL = cooked(8, 0.85f);

    public static final FoodProperties CRIMSON_FANGJAW = raw(4, 0.4f);
    public static final FoodProperties COOKED_CRIMSON_FANGJAW = cooked(8, 0.85f);

    public static final FoodProperties WARPED_GUPPY = raw(2, 0.3f);
    public static final FoodProperties COOKED_WARPED_GUPPY = cooked(6, 0.75f);

    // 8. The End Dimension
    public static final FoodProperties VOID_TETRA = raw(3, 0.35f);
    public static final FoodProperties COOKED_VOID_TETRA = cooked(7, 0.8f);

    public static final FoodProperties CHORUS_DRIFTFISH = raw(3, 0.4f);
    public static final FoodProperties COOKED_CHORUS_DRIFTFISH = cooked(8, 0.85f);
}
