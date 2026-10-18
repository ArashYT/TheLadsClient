package com.thelads.smp.features.arise.nb10;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

/**
 * Fairy lights string item. Light bulbs glow with emissive intensity in the specified dye color or rainbow.
 */
public class HangingLightsItem extends HangingDecorationItem {

    public static final int[] WARM_WHITE = HangingPalettes.WARM_WHITE;
    public static final int[] RAINBOW = HangingPalettes.RAINBOW;

    public HangingLightsItem(Item.Properties properties, int[] colors) {
        super(properties, DecorationType.LIGHTS, colors);
    }

    public static HangingLightsItem createDyed(Item.Properties properties, DyeColor dyeColor) {
        return new HangingLightsItem(properties, new int[] { dyeColor.getTextureDiffuseColor() });
    }
}
