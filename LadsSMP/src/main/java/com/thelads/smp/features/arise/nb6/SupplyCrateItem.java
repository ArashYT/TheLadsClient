package com.thelads.smp.features.arise.nb6;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Supply Crate item fished rarely from water bodies.
 * Right-clicking opens the crate and yields valuable maritime/survival supplies.
 */
public class SupplyCrateItem extends Item {
    public SupplyCrateItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.8f, 1.1f);

        if (!level.isClientSide()) {
            stack.consume(1, player);
            RandomSource rand = level.getRandom();
            List<ItemStack> loot = generateLoot(rand);
            for (ItemStack item : loot) {
                if (!player.getInventory().add(item)) {
                    Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), item);
                }
            }
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(
                        Component.literal("§6[Supply Crate] §7Opened and salvaged maritime goods!"),
                        true
                );
            }
        }

        return InteractionResult.SUCCESS;
    }

    public static List<ItemStack> generateLoot(RandomSource rand) {
        List<ItemStack> items = new ArrayList<>();
        int count = 2 + rand.nextInt(3); // 2 to 4 items
        for (int i = 0; i < count; i++) {
            int roll = rand.nextInt(100);
            if (roll < 25) {
                items.add(new ItemStack(Items.IRON_INGOT, 1 + rand.nextInt(3)));
            } else if (roll < 45) {
                items.add(new ItemStack(Items.STRING, 2 + rand.nextInt(4)));
            } else if (roll < 65) {
                items.add(new ItemStack(Items.GOLD_NUGGET, 3 + rand.nextInt(6)));
            } else if (roll < 80) {
                items.add(new ItemStack(Items.LAPIS_LAZULI, 2 + rand.nextInt(4)));
            } else if (roll < 92) {
                items.add(new ItemStack(Items.EMERALD, 1 + rand.nextInt(2)));
            } else if (roll < 97) {
                items.add(new ItemStack(Items.PRISMARINE_SHARD, 1 + rand.nextInt(3)));
            } else {
                items.add(new ItemStack(Items.NAUTILUS_SHELL, 1));
            }
        }
        return items;
    }
}
