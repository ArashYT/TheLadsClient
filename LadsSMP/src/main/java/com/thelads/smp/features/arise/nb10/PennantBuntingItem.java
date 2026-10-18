package com.thelads.smp.features.arise.nb10;

import net.minecraft.world.item.Item;

/**
 * Pennant bunting decoration item. Displays colorful triangular flags hanging along a cord.
 */
public class PennantBuntingItem extends HangingDecorationItem {

    public static final int[] FESTIVE_PENNANTS = HangingPalettes.FESTIVE_PENNANTS;

    public PennantBuntingItem(Item.Properties properties) {
        super(properties, DecorationType.PENNANT, FESTIVE_PENNANTS);
    }
}
