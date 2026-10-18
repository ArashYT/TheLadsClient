package com.thelads.smp.features.arise.nb10;

/**
 * Pure color palettes for hanging decorations (fairy lights, bunting, pine garland, tinsel).
 * Standalone constant definitions completely decoupled from Item or Block registration lifecycles.
 */
public final class HangingPalettes {

    private HangingPalettes() {}

    public static final int[] WARM_WHITE = new int[] { 0xFFE484 };

    public static final int[] RAINBOW = new int[] {
            0xFF2020, // Red
            0xFFA020, // Orange
            0xFFFF20, // Yellow
            0x20FF20, // Lime
            0x20FFFF, // Cyan
            0x2060FF, // Blue
            0xA020FF, // Purple
            0xFF20A0  // Magenta
    };

    public static final int[] FESTIVE_PENNANTS = new int[] {
            0xD83030, // Red
            0x3088E0, // Blue
            0xF0D020, // Yellow
            0x38B038, // Green
            0xE060B0  // Pink
    };

    public static final int[] PINE_GARLAND = new int[] {
            0x2D5E1E,
            0x3B7A28,
            0x1E4214
    };

    public static final int[] SILVER_TINSEL = new int[] {
            0xE0E0E0,
            0xF8F8F8,
            0xD0D8E0
    };
}
