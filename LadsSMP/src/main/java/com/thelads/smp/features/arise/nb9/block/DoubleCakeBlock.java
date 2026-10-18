package com.thelads.smp.features.arise.nb9.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 2-tier wedding-style celebration cake.
 * Top tier is consumed first slice by slice, followed by the bottom tier.
 */
public class DoubleCakeBlock extends Block {
    public static final IntegerProperty TOP_BITES = IntegerProperty.create("top_bites", 0, 6);
    public static final IntegerProperty BOTTOM_BITES = CakeBlock.BITES;

    private static final VoxelShape FULL_DOUBLE_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    private static final VoxelShape BOTTOM_ONLY_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 8.0, 15.0);

    public DoubleCakeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(TOP_BITES, 0)
                .setValue(BOTTOM_BITES, 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP_BITES) < 6 ? FULL_DOUBLE_SHAPE : BOTTOM_ONLY_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP_BITES, BOTTOM_BITES);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return eatSlice(level, pos, state, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        return eatSlice(level, pos, state, player);
    }

    private InteractionResult eatSlice(Level level, BlockPos pos, BlockState state, Player player) {
        if (!player.canEat(false)) {
            return InteractionResult.PASS;
        }

        player.awardStat(Stats.EAT_CAKE_SLICE);
        player.getFoodData().eat(2, 0.1F);
        level.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);

        int topBites = state.getValue(TOP_BITES);
        if (topBites < 6) {
            level.setBlock(pos, state.setValue(TOP_BITES, topBites + 1), 3);
        } else {
            int bottomBites = state.getValue(BOTTOM_BITES);
            if (bottomBites < 6) {
                level.setBlock(pos, state.setValue(BOTTOM_BITES, bottomBites + 1), 3);
            } else {
                level.removeBlock(pos, false);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
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
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, scheduledTicks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops()) {
            int topBites = state.getValue(TOP_BITES);
            int bottomBites = state.getValue(BOTTOM_BITES);
            if (topBites == 0 && bottomBites == 0) {
                popResource(level, pos, new ItemStack(Items.CAKE, 2));
            } else if (bottomBites == 0) {
                popResource(level, pos, new ItemStack(Items.CAKE, 1));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, builder));
        int topBites = state.getValue(TOP_BITES);
        int bottomBites = state.getValue(BOTTOM_BITES);
        if (topBites == 0 && bottomBites == 0) {
            drops.add(new ItemStack(Items.CAKE, 2));
        } else if (bottomBites == 0) {
            drops.add(new ItemStack(Items.CAKE, 1));
        }
        return drops;
    }
}
