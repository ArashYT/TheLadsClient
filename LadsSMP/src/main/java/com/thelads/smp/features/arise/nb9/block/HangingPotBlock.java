package com.thelads.smp.features.arise.nb9.block;

import com.thelads.smp.features.arise.nb9.entity.HangingPotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Flower pot suspended from a ceiling or chain.
 * Supports plant insertion, extraction, and safe zero-void drops.
 */
public class HangingPotBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0);

    public HangingPotBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HangingPotBlockEntity(pos, state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN) || level.getBlockState(above).getBlock() instanceof ChainBlock;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess scheduledTicks,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            net.minecraft.util.RandomSource random
    ) {
        return direction == Direction.UP && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, scheduledTicks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof HangingPotBlockEntity be) {
            if ("minecraft:air".equals(be.getPlantItemId()) && isPottable(stack)) {
                if (!level.isClientSide()) {
                    be.setPlantItemId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof HangingPotBlockEntity be) {
            if (!"minecraft:air".equals(be.getPlantItemId())) {
                if (!level.isClientSide()) {
                    ItemStack plant = be.createPlantDrop();
                    be.setPlantItemId("minecraft:air");
                    if (!player.addItem(plant)) {
                        popResource(level, pos, plant);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    private boolean isPottable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof BlockItem bi) {
            Block b = bi.getBlock();
            return b instanceof BushBlock || b instanceof BambooStalkBlock || b instanceof CactusBlock;
        }
        return false;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            if (level.getBlockEntity(pos) instanceof HangingPotBlockEntity be) {
                ItemStack plant = be.createPlantDrop();
                if (!plant.isEmpty()) {
                    popResource(level, pos, plant);
                }
                popResource(level, pos, new ItemStack(Items.FLOWER_POT));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        drops.add(new ItemStack(Items.FLOWER_POT));
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof HangingPotBlockEntity potBe) {
            ItemStack plant = potBe.createPlantDrop();
            if (!plant.isEmpty()) {
                drops.add(plant);
            }
        }
        return drops;
    }
}
