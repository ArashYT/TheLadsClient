package com.thelads.smp.features.arise.nb3.botanypots;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Botany Pots Feature:
 * Growing crops and saplings in automated, compact Botany Pots.
 */
public final class BotanyPotsFeature {
    private BotanyPotsFeature() {}

    public static boolean isValidSoil(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == Items.DIRT || item == Items.COARSE_DIRT || item == Items.FARMLAND
                || item == Items.MUD || item == Items.ROOTED_DIRT
                || item == Items.SAND || item == Items.RED_SAND
                || item == Items.SOUL_SAND || item == Items.SOUL_SOIL;
    }

    public static boolean isValidCrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == Items.WHEAT_SEEDS || item == Items.CARROT || item == Items.POTATO
                || item == Items.BEETROOT_SEEDS || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS
                || item == Items.NETHER_WART || item == Items.SWEET_BERRIES
                || item == Items.SUGAR_CANE || item == Items.CACTUS
                || item == Items.OAK_SAPLING || item == Items.SPRUCE_SAPLING || item == Items.BIRCH_SAPLING
                || item == Items.JUNGLE_SAPLING || item == Items.ACACIA_SAPLING || item == Items.DARK_OAK_SAPLING
                || item == Items.CHERRY_SAPLING || item == Items.MANGROVE_PROPAGULE
                || item == Items.BAMBOO;
    }

    /**
     * Resolves the primary harvest yield for a grown crop item.
     */
    public static ItemStack getYield(ItemStack seed) {
        if (seed == null || seed.isEmpty()) return ItemStack.EMPTY;
        var item = seed.getItem();
        if (item == Items.WHEAT_SEEDS) return new ItemStack(Items.WHEAT, 2);
        if (item == Items.CARROT) return new ItemStack(Items.CARROT, 3);
        if (item == Items.POTATO) return new ItemStack(Items.POTATO, 3);
        if (item == Items.BEETROOT_SEEDS) return new ItemStack(Items.BEETROOT, 2);
        if (item == Items.MELON_SEEDS) return new ItemStack(Items.MELON_SLICE, 4);
        if (item == Items.PUMPKIN_SEEDS) return new ItemStack(Items.PUMPKIN, 1);
        if (item == Items.NETHER_WART) return new ItemStack(Items.NETHER_WART, 3);
        if (item == Items.SWEET_BERRIES) return new ItemStack(Items.SWEET_BERRIES, 3);
        if (item == Items.SUGAR_CANE) return new ItemStack(Items.SUGAR_CANE, 2);
        if (item == Items.CACTUS) return new ItemStack(Items.CACTUS, 2);
        if (item == Items.OAK_SAPLING) return new ItemStack(Items.OAK_LOG, 2);
        if (item == Items.SPRUCE_SAPLING) return new ItemStack(Items.SPRUCE_LOG, 2);
        if (item == Items.BIRCH_SAPLING) return new ItemStack(Items.BIRCH_LOG, 2);
        if (item == Items.JUNGLE_SAPLING) return new ItemStack(Items.JUNGLE_LOG, 2);
        if (item == Items.ACACIA_SAPLING) return new ItemStack(Items.ACACIA_LOG, 2);
        if (item == Items.DARK_OAK_SAPLING) return new ItemStack(Items.DARK_OAK_LOG, 2);
        if (item == Items.CHERRY_SAPLING) return new ItemStack(Items.CHERRY_LOG, 2);
        if (item == Items.MANGROVE_PROPAGULE) return new ItemStack(Items.MANGROVE_LOG, 2);
        if (item == Items.BAMBOO) return new ItemStack(Items.BAMBOO, 3);
        return new ItemStack(item, 1);
    }
}
