package com.thelads.smp.features.arise.nb9;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Helper for ringing bells by interacting with connected chain columns.
 */
public final class BellRingingHelper {

    public static boolean ringConnectedBell(Player player, Level level, BlockPos startChainPos, int maxDistance) {
        if (!(level.getBlockState(startChainPos).getBlock() instanceof ChainBlock)) {
            return false;
        }

        // Search upward
        BlockPos current = startChainPos;
        for (int i = 0; i < maxDistance; i++) {
            current = current.above();
            BlockState state = level.getBlockState(current);
            if (state.getBlock() instanceof BellBlock bell) {
                return ringBell(player, level, current, bell);
            }
            if (!(state.getBlock() instanceof ChainBlock)) {
                break;
            }
        }

        // Search downward
        current = startChainPos;
        for (int i = 0; i < maxDistance; i++) {
            current = current.below();
            BlockState state = level.getBlockState(current);
            if (state.getBlock() instanceof BellBlock bell) {
                return ringBell(player, level, current, bell);
            }
            if (!(state.getBlock() instanceof ChainBlock)) {
                break;
            }
        }

        return false;
    }

    private static boolean ringBell(Player player, Level level, BlockPos bellPos, BellBlock bell) {
        if (!level.isClientSide()) {
            bell.attemptToRing(player, level, bellPos, null);
            level.playSound(null, bellPos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
        }
        return true;
    }
}
