package com.thelads.smp.features.arise.nb9;

import com.thelads.smp.features.arise.nb9.block.CarpetedStairsBlock;
import com.thelads.smp.features.arise.nb9.block.SkullCandleBlock;
import com.thelads.smp.features.arise.nb9.block.SkullPileBlock;
import com.thelads.smp.features.arise.nb9.block.WallLanternBlock;
import com.thelads.smp.features.arise.nb9.entity.CarpetedSlabBlockEntity;
import com.thelads.smp.features.arise.nb9.entity.CarpetedStairsBlockEntity;
import com.thelads.smp.features.arise.nb9.entity.SkullCandleBlockEntity;
import com.thelads.smp.features.arise.nb9.entity.SkullPileBlockEntity;
import com.thelads.smp.features.arise.nb9.entity.ToolHookBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Global UseBlockCallback handler implementing Amendments-style block interactions.
 */
public final class AmendmentsBlockInteractions {

    public static InteractionResult handleUseBlock(
            Player player,
            Level level,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        AriseNB9Config config = AriseNB9Config.get();
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = player.getItemInHand(hand);

        // 1. Cauldrons
        if (state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.CAULDRON)) {
            InteractionResult res = CauldronInteractionHelper.handleWaterCauldron(held, state, level, pos, player, hand);
            if (res != InteractionResult.PASS) return res;
        }

        // 2. Wall Lanterns
        if (config.wallLanterns && (held.is(Items.LANTERN) || held.is(Items.SOUL_LANTERN))) {
            Direction face = hitResult.getDirection();
            if (face.getAxis().isHorizontal()) {
                BlockPos targetPos = pos.relative(face);
                if (level.getBlockState(targetPos).canBeReplaced() && state.isFaceSturdy(level, pos, face)) {
                    if (!level.isClientSide()) {
                        boolean isSoul = held.is(Items.SOUL_LANTERN);
                        BlockState wallLanternState = (isSoul ? AriseNB9Features.SOUL_WALL_LANTERN : AriseNB9Features.WALL_LANTERN)
                                .defaultBlockState().setValue(WallLanternBlock.FACING, face);
                        level.setBlock(targetPos, wallLanternState, 3);
                        level.playSound(null, targetPos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

                        if (!player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }

        // 3. Hanging Signs Items
        if (config.itemsOnSigns && HangingSignItemManager.isHangingSign(state)) {
            InteractionResult res = HangingSignItemManager.handleSignClick(player, level, hand, pos, state);
            if (res != InteractionResult.PASS) return res;
        }

        // 4. Bell Chain Ringing
        if (config.chainRinging && state.getBlock() instanceof ChainBlock) {
            if (BellRingingHelper.ringConnectedBell(player, level, pos, config.chainLength)) {
                return InteractionResult.SUCCESS;
            }
        }

        // 5. Tripwire Hook -> Tool Hook
        if (config.toolHook && state.is(Blocks.TRIPWIRE_HOOK) && isToolOrWeapon(held)) {
            if (!level.isClientSide()) {
                Direction facing = state.getValue(TripWireHookBlock.FACING);
                BlockState hookState = AriseNB9Features.TOOL_HOOK.defaultBlockState().setValue(WallLanternBlock.FACING, facing);
                level.setBlock(pos, hookState, 3);

                if (level.getBlockEntity(pos) instanceof ToolHookBlockEntity be) {
                    be.setToolStack(held.copyWithCount(1));
                }

                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // 6. Skull Pile & Skull Candle
        if (state.getBlock() instanceof SkullBlock) {
            // Skull candle
            if (config.skullCandles && held.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CandleBlock) {
                if (!level.isClientSide()) {
                    int rotation = state.getValue(SkullBlock.ROTATION);
                    String skullId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    String candleId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

                    BlockState candleSkullState = AriseNB9Features.SKULL_CANDLE.defaultBlockState()
                            .setValue(SkullCandleBlock.ROTATION, rotation)
                            .setValue(SkullCandleBlock.CANDLES, 1)
                            .setValue(SkullCandleBlock.LIT, false);
                    level.setBlock(pos, candleSkullState, 3);

                    if (level.getBlockEntity(pos) instanceof SkullCandleBlockEntity be) {
                        be.setSkullItemId(skullId);
                        be.setCandleItemId(candleId);
                    }

                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.CANDLE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }

            // Skull pile
            if (config.skullPiles && isSkullItem(held)) {
                if (!level.isClientSide()) {
                    int rotation = state.getValue(SkullBlock.ROTATION);
                    String bottomId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    String topId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

                    BlockState pileState = AriseNB9Features.SKULL_PILE.defaultBlockState()
                            .setValue(SkullPileBlock.ROTATION, rotation);
                    level.setBlock(pos, pileState, 3);

                    if (level.getBlockEntity(pos) instanceof SkullPileBlockEntity be) {
                        be.setBottomSkullId(bottomId);
                        be.setTopSkullId(topId);
                    }

                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        }

        // 7. Double Cake
        if (config.doubleCake && state.is(Blocks.CAKE) && state.getValue(net.minecraft.world.level.block.CakeBlock.BITES) == 0 && held.is(Items.CAKE)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, AriseNB9Features.DOUBLE_CAKE.defaultBlockState(), 3);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // 8. Carpet on Stairs
        if (config.carpetedStairs && state.getBlock() instanceof StairBlock && held.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CarpetBlock) {
            if (!level.isClientSide()) {
                String stairId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                String carpetId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

                BlockState carpeted = AriseNB9Features.CARPETED_STAIRS.defaultBlockState()
                        .setValue(CarpetedStairsBlock.FACING, state.getValue(StairBlock.FACING))
                        .setValue(CarpetedStairsBlock.HALF, state.getValue(StairBlock.HALF))
                        .setValue(CarpetedStairsBlock.SHAPE, state.getValue(StairBlock.SHAPE))
                        .setValue(CarpetedStairsBlock.WATERLOGGED, state.getValue(StairBlock.WATERLOGGED));
                level.setBlock(pos, carpeted, 3);

                if (level.getBlockEntity(pos) instanceof CarpetedStairsBlockEntity be) {
                    be.setBaseStairBlockId(stairId);
                    be.setCarpetItemId(carpetId);
                }

                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // 9. Carpet on Slabs
        if (config.carpetedSlabs && state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && held.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CarpetBlock) {
            if (!level.isClientSide()) {
                String slabId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                String carpetId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

                BlockState carpeted = AriseNB9Features.CARPETED_SLAB.defaultBlockState()
                        .setValue(CarpetedStairsBlock.WATERLOGGED, state.getValue(SlabBlock.WATERLOGGED));
                level.setBlock(pos, carpeted, 3);

                if (level.getBlockEntity(pos) instanceof CarpetedSlabBlockEntity be) {
                    be.setBaseSlabBlockId(slabId);
                    be.setCarpetItemId(carpetId);
                }

                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // 10. Hanging Flower Pot
        if (config.hangingPot && held.is(Items.FLOWER_POT)) {
            Direction face = hitResult.getDirection();
            if (face == Direction.DOWN) {
                BlockPos targetPos = pos.below();
                if (level.getBlockState(targetPos).canBeReplaced()) {
                    if (!level.isClientSide()) {
                        level.setBlock(targetPos, AriseNB9Features.HANGING_POT.defaultBlockState(), 3);
                        if (!player.getAbilities().instabuild) {
                            held.shrink(1);
                        }
                        level.playSound(null, targetPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }

    private static boolean isToolOrWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.MAX_DAMAGE)
                || stack.is(Items.BOW)
                || stack.is(Items.CROSSBOW)
                || stack.is(Items.TRIDENT)
                || stack.is(Items.SHEARS)
                || stack.is(Items.FISHING_ROD)
                || stack.is(Items.FLINT_AND_STEEL);
    }

    private static boolean isSkullItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.SKELETON_SKULL)
                || stack.is(Items.WITHER_SKELETON_SKULL)
                || stack.is(Items.ZOMBIE_HEAD)
                || stack.is(Items.PLAYER_HEAD)
                || stack.is(Items.CREEPER_HEAD)
                || stack.is(Items.PIGLIN_HEAD);
    }
}
