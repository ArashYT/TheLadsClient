package com.thelads.smp.features;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consolidates 'stickyenchantinglapis': Stores Lapis Lazuli count inside EnchantingTableBlockEntity components
 * so Lapis remains in the Enchanting Table across sessions and drops when the table is broken.
 */
public final class StickyLapisManager {
    private static final Map<UUID, BlockPos> LAST_ENCHANTING_TABLE_INTERACTION = new ConcurrentHashMap<>();

    private StickyLapisManager() {}

    public static void recordInteraction(Player player, BlockPos pos) {
        if (player != null && pos != null) {
            LAST_ENCHANTING_TABLE_INTERACTION.put(player.getUUID(), pos.immutable());
        }
    }

    @Nullable
    public static BlockPos getLastInteraction(UUID playerUuid) {
        return playerUuid != null ? LAST_ENCHANTING_TABLE_INTERACTION.get(playerUuid) : null;
    }

    @Nullable
    public static EnchantingTableBlockEntity getEnchantingTableBlockEntity(Player player) {
        if (player == null) return null;
        BlockPos pos = LAST_ENCHANTING_TABLE_INTERACTION.get(player.getUUID());
        if (pos == null) return null;
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof EnchantingTableBlockEntity etbe ? etbe : null;
    }

    public static int getLapisCount(EnchantingTableBlockEntity be) {
        if (be == null) return 0;
        return be.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 0);
    }

    public static void saveLapisCount(Level level, EnchantingTableBlockEntity be, int count) {
        if (be == null) return;
        int clamped = Math.clamp(count, 0, 64);
        be.setComponents(DataComponentMap.builder()
                .addAll(be.components())
                .set(DataComponents.MAX_STACK_SIZE, clamped)
                .build());
        be.setChanged();
        if (level != null) {
            BlockPos pos = be.getBlockPos();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
}
