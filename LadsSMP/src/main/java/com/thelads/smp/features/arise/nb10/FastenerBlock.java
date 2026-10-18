package com.thelads.smp.features.arise.nb10;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Directional fastener bracket block that can be placed against fences, walls, ceilings, and solid blocks.
 * Hosts a FastenerBlockEntity to manage and persist string connections.
 */
public class FastenerBlock extends DirectionalBlock implements EntityBlock {

    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final BooleanProperty HAS_LIGHTS = BooleanProperty.create("has_lights");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private static final VoxelShape SHAPE_UP = Block.box(5.0, 0.0, 5.0, 11.0, 4.0, 11.0);
    private static final VoxelShape SHAPE_DOWN = Block.box(5.0, 12.0, 5.0, 11.0, 16.0, 11.0);
    private static final VoxelShape SHAPE_NORTH = Block.box(5.0, 5.0, 12.0, 11.0, 11.0, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 4.0);
    private static final VoxelShape SHAPE_WEST = Block.box(12.0, 5.0, 5.0, 16.0, 11.0, 11.0);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0, 5.0, 5.0, 4.0, 11.0, 11.0);

    public FastenerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(HAS_LIGHTS, false)
                .setValue(POWERED, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FastenerBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_LIGHTS, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        return defaultBlockState().setValue(FACING, face);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction dir = state.getValue(FACING);
        return switch (dir) {
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_UP;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState supportState = level.getBlockState(supportPos);

        if (supportState.is(BlockTags.FENCES) || supportState.is(BlockTags.WALLS)) {
            return true;
        }
        return Block.canSupportCenter(level, supportPos, facing) || Block.canSupportRigidBlock(level, supportPos);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess tickAccess,
            BlockPos pos,
            Direction direction,
            BlockPos neighborPos,
            BlockState neighborState,
            RandomSource random
    ) {
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite() && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (player.isShiftKeyDown() || player.getMainHandItem().is(net.minecraft.world.item.Items.SHEARS)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FastenerBlockEntity fastenerBe) {
                List<HangingConnection> conns = fastenerBe.getConnections();
                if (!conns.isEmpty()) {
                    if (!level.isClientSide()) {
                        HangingConnection last = conns.get(conns.size() - 1);
                        fastenerBe.removeConnection(last.getId());
                        level.playSound(null, pos, SoundEvents.SHEARS_SNIP, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            @Nullable Orientation orientation,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;

        boolean hasSignal = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);

        if (hasSignal != wasPowered) {
            level.setBlock(pos, state.setValue(POWERED, hasSignal), Block.UPDATE_ALL);
            if (hasSignal && AriseNB10Config.get().christmasJingles) {
                // Play festive chime when powered
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.5F, 1.4F);
            }
        }
    }
}
