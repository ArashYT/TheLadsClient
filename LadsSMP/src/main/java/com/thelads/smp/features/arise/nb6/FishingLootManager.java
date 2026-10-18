package com.thelads.smp.features.arise.nb6;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Manages biome-based fishing loot selection, supply crates, and Luck of the Sea scaling.
 */
public final class FishingLootManager {
    private static final Map<FishingBiomeGroup, List<String>> POOLS = new EnumMap<>(FishingBiomeGroup.class);

    static {
        POOLS.put(FishingBiomeGroup.FRESHWATER, List.of("bass", "bluegill", "carp", "trout"));
        POOLS.put(FishingBiomeGroup.COLD, List.of("glacier_cod", "frostbite_flounder", "trout"));
        POOLS.put(FishingBiomeGroup.WARM_OCEAN, List.of("tuna", "snapper", "sailfish"));
        POOLS.put(FishingBiomeGroup.SWAMP, List.of("catfish", "bullhead", "carp"));
        POOLS.put(FishingBiomeGroup.CAVE, List.of("abyssal_angler", "cave_tetra"));
        POOLS.put(FishingBiomeGroup.NETHER, List.of("magma_mackerel", "crimson_fangjaw", "warped_guppy"));
        POOLS.put(FishingBiomeGroup.THE_END, List.of("void_tetra", "chorus_driftfish"));
        POOLS.put(FishingBiomeGroup.NIGHT, List.of("lunar_guppy", "abyssal_angler", "catfish"));
        POOLS.put(FishingBiomeGroup.STORM, List.of("storm_perch", "bluegill", "sailfish"));
    }

    private FishingLootManager() {}

    public static ItemStack rollLoot(FishingHook hook, Player player, ItemStack rod) {
        if (!AriseNB6Config.get().customFishLoot) {
            return new ItemStack(Items.COD);
        }

        Level level = hook.level();
        RandomSource rand = level.getRandom();
        BlockPos pos = hook.blockPosition();

        int luckBonus = 0;
        if (level instanceof ServerLevel serverLevel) {
            luckBonus = EnchantmentHelper.getFishingLuckBonus(serverLevel, rod, player);
        }
        float playerLuck = player != null ? player.getLuck() : 0.0f;
        float totalLuck = luckBonus + playerLuck;

        // 1. Supply Crate check
        int crateWeight = AriseNB6Config.get().crateWeight;
        float crateChance = (crateWeight / 100.0f) + (totalLuck * 0.015f);
        if (rand.nextFloat() < crateChance && AriseNB6Items.SUPPLY_CRATE != null) {
            return new ItemStack(AriseNB6Items.SUPPLY_CRATE);
        }

        // 2. Select Biome Group
        FishingBiomeGroup group = FishingBiomeGroup.evaluate(level, pos);
        List<String> pool = POOLS.getOrDefault(group, POOLS.get(FishingBiomeGroup.FRESHWATER));

        // 3. Roll species from pool
        String chosenId = pool.get(rand.nextInt(pool.size()));
        Item item = AriseNB6Items.getItem(chosenId);
        if (item != null) {
            return new ItemStack(item);
        }

        return new ItemStack(Items.SALMON);
    }

    public static List<String> getPool(FishingBiomeGroup group) {
        return POOLS.getOrDefault(group, Collections.emptyList());
    }
}
