package com.thelads.smp.features.arise.nb10;

import net.minecraft.world.item.Item;

/**
 * Natural leafy garland and festive tinsel items.
 */
public class GarlandItem extends HangingDecorationItem {

    public static final int[] PINE_GARLAND = HangingPalettes.PINE_GARLAND;
    public static final int[] SILVER_TINSEL = HangingPalettes.SILVER_TINSEL;

    public GarlandItem(Item.Properties properties, boolean isTinsel) {
        super(properties, isTinsel ? DecorationType.TINSEL : DecorationType.GARLAND, isTinsel ? SILVER_TINSEL : PINE_GARLAND);
    }
}
