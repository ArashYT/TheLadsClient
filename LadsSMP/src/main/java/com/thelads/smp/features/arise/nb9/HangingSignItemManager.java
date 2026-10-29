package com.thelads.smp.features.arise.nb9;

import com.thelads.smp.features.arise.nb1.AdminClaimsFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for displaying items on hanging signs.
 * Connects directly to SignBlockEntity (via HangingSignItemHolder) for disk/chunk NBT persistence,
 * with thread-safe tracking, safe item retrieval, and zero-void drop safety.
 */
public final class HangingSignItemManager {
    // Fallback cache used for unit tests or environments without a running world
    private static final Map<BlockPos, ItemStack> TEST_FALLBACK = new ConcurrentHashMap<>();

    public static boolean isHangingSign(BlockState state) {
        return state.getBlock() instanceof CeilingHangingSignBlock || state.getBlock() instanceof WallHangingSignBlock;
    }

    public static ItemStack getDisplayedItem(Level level, BlockPos pos) {
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HangingSignItemHolder holder) {
                return holder.ladssmp$getDisplayedItem();
            }
        }
        return TEST_FALLBACK.getOrDefault(pos, ItemStack.EMPTY);
    }

    public static ItemStack getDisplayedItem(BlockPos pos) {
        return TEST_FALLBACK.getOrDefault(pos, ItemStack.EMPTY);
    }

    public static void setDisplayedItem(Level level, BlockPos pos, ItemStack item) {
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HangingSignItemHolder holder) {
                holder.ladssmp$setDisplayedItem(item);
                return;
            }
        }
        setDisplayedItem(pos, item);
    }

    public static void setDisplayedItem(BlockPos pos, ItemStack item) {
        if (item == null || item.isEmpty()) {
            TEST_FALLBACK.remove(pos);
        } else {
            TEST_FALLBACK.put(pos, item.copy());
        }
    }

    public static void onSignRemoved(Level level, BlockPos pos, BlockEntity be) {
        if (be instanceof HangingSignItemHolder holder) {
            ItemStack item = holder.ladssmp$getDisplayedItem();
            if (!item.isEmpty()) {
                Block.popResource(level, pos, item);
                holder.ladssmp$setDisplayedItem(ItemStack.EMPTY);
            }
        }
        ItemStack fallback = TEST_FALLBACK.remove(pos);
        if (fallback != null && !fallback.isEmpty()) {
            Block.popResource(level, pos, fallback);
        }
    }

    public static InteractionResult handleSignClick(
            Player player,
            Level level,
            InteractionHand hand,
            BlockPos pos,
            BlockState state
    ) {
        if (!AriseNB9Config.get().itemsOnSigns || !isHangingSign(state)) {
            return InteractionResult.PASS;
        }

        if (player != null) {
            if (!player.mayBuild() || (level != null && !level.mayInteract(player, pos))) {
                return InteractionResult.PASS;
            }
            if (level != null && AdminClaimsFeature.isProtected(level, pos, player)) {
                return InteractionResult.PASS;
            }
        }

        ItemStack held = player.getItemInHand(hand);

        // Do not intercept standard sign text editing items
        if (held.getItem() instanceof DyeItem || held.getItem() instanceof HoneycombItem || held.is(Items.INK_SAC) || held.is(Items.GLOW_INK_SAC)) {
            return InteractionResult.PASS;
        }

        ItemStack existing = getDisplayedItem(level, pos);

        // Retrieve existing item with empty sneak hand
        if (!existing.isEmpty() && held.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                setDisplayedItem(level, pos, ItemStack.EMPTY);
                if (!player.addItem(existing)) {
                    Block.popResource(level, pos, existing);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // Attach held item if currently empty
        if (existing.isEmpty() && !held.isEmpty()) {
            if (!level.isClientSide()) {
                ItemStack toAttach = held.copyWithCount(1);
                setDisplayedItem(level, pos, toAttach);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
