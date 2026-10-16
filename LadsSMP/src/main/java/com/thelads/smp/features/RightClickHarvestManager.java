package com.thelads.smp.features;

import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.mixin.MixinCropBlockAccessor;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Consolidates 'rightclickharvest': Allows right-clicking mature crops, nether wart, cocoa beans,
 * sugar cane, and cactus to harvest & replant in place, with tiered hoe radius harvesting.
 */
public final class RightClickHarvestManager {

    private static final Direction[] CARDINAL_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private RightClickHarvestManager() {}

    public static void init() {
        UseBlockCallback.EVENT.register(RightClickHarvestManager::onBlockUse);
    }

    private static InteractionResult onBlockUse(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
        if (!LadsConfig.get().features.rightClickHarvest) {
            return InteractionResult.PASS;
        }
        if (player.isSpectator() || player.isCrouching() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }

        Block block = state.getBlock();
        if (block instanceof CropBlock || block instanceof NetherWartBlock || block instanceof CocoaBlock) {
            if (!isMature(state)) {
                return InteractionResult.PASS;
            }

            ItemStack held = player.getMainHandItem();
            boolean usedHoe = held.is(ItemTags.HOES);
            int radius = getHoeRadius(held);
            boolean circle = isHoeCircle(held);

            harvestAndReplant(level, pos, state, player, held);

            if (radius > 0 && (block instanceof CropBlock || block instanceof NetherWartBlock)) {
                if (radius == 1 && circle) {
                    for (Direction dir : CARDINAL_DIRECTIONS) {
                        BlockPos targetPos = pos.relative(dir);
                        BlockState targetState = level.getBlockState(targetPos);
                        if ((targetState.getBlock() instanceof CropBlock || targetState.getBlock() instanceof NetherWartBlock) && isMature(targetState)) {
                            harvestAndReplant(level, targetPos, targetState, player, held);
                        }
                    }
                } else {
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            if (circle && dx * dx + dz * dz > radius * radius + 1) continue;
                            BlockPos targetPos = pos.offset(dx, 0, dz);
                            BlockState targetState = level.getBlockState(targetPos);
                            if ((targetState.getBlock() instanceof CropBlock || targetState.getBlock() instanceof NetherWartBlock) && isMature(targetState)) {
                                harvestAndReplant(level, targetPos, targetState, player, held);
                            }
                        }
                    }
                }
            }

            if (usedHoe && !level.isClientSide()) {
                held.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
            return InteractionResult.SUCCESS;
        } else if (block instanceof SugarCaneBlock || block instanceof CactusBlock) {
            if (hitResult.getDirection() == Direction.UP && heldMatchesBlock(player.getMainHandItem(), block)) {
                return InteractionResult.PASS;
            }
            // Find the bottom block of the column
            BlockPos bottom = pos;
            while (level.getBlockState(bottom.below()).is(block)) {
                bottom = bottom.below();
            }
            // Only harvest if the column has at least 2 blocks
            BlockPos firstBreak = bottom.above();
            if (!level.getBlockState(firstBreak).is(block)) {
                return InteractionResult.PASS;
            }
            // Break from the top down to firstBreak so the bottom block stays planted
            BlockPos top = firstBreak;
            while (level.getBlockState(top.above()).is(block)) {
                top = top.above();
            }
            if (!level.isClientSide()) {
                for (BlockPos cur = top; !cur.equals(bottom); cur = cur.below()) {
                    level.destroyBlock(cur, true, player);
                }
                player.causeFoodExhaustion(0.005f);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static boolean heldMatchesBlock(ItemStack stack, Block block) {
        return (block instanceof SugarCaneBlock && stack.is(Items.SUGAR_CANE))
                || (block instanceof CactusBlock && stack.is(Items.CACTUS));
    }

    public static boolean isMature(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        } else if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        } else if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        return false;
    }

    public static BlockState getReplantState(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return state.setValue(((MixinCropBlockAccessor) crop).ladssmp$invokeGetAgeProperty(), 0);
        } else if (block instanceof NetherWartBlock) {
            return state.setValue(NetherWartBlock.AGE, 0);
        } else if (block instanceof CocoaBlock) {
            return state.setValue(CocoaBlock.AGE, 0);
        }
        return state;
    }

    private static void harvestAndReplant(Level level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        BlockState replanted = getReplantState(state);
        level.setBlockAndUpdate(pos, replanted);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, replanted));

        if (level instanceof ServerLevel serverLevel) {
            Item replantSeed = state.getCloneItemStack(serverLevel, pos, true).getItem();
            boolean removedSeed = false;
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, level.getBlockEntity(pos), player, tool);
            for (ItemStack drop : drops) {
                if (!removedSeed && drop.is(replantSeed)) {
                    drop.shrink(1);
                    removedSeed = true;
                }
                if (!drop.isEmpty()) {
                    Block.popResource(level, pos, drop);
                }
            }
            state.spawnAfterBreak(serverLevel, pos, tool, true);
            player.causeFoodExhaustion(0.005f);
            level.playSound(
                    null,
                    pos,
                    state.getBlock() instanceof NetherWartBlock ? SoundEvents.NETHER_WART_PLANTED : SoundEvents.CROP_PLANTED,
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f
            );
        }
    }

    public static int getHoeRadius(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ItemTags.HOES)) {
            return 0;
        }
        if (stack.is(Items.NETHERITE_HOE) || stack.is(Items.DIAMOND_HOE)) {
            return 2;
        }
        return 1;
    }

    public static boolean isHoeCircle(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ItemTags.HOES)) {
            return true;
        }
        if (stack.is(Items.NETHERITE_HOE) || stack.is(Items.IRON_HOE) || stack.is(Items.GOLDEN_HOE) || stack.is(Items.COPPER_HOE)) {
            return false;
        }
        return true;
    }
}
