package com.thelads.smp.features.arise.nb3.botanypots;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Standard decorative Botany Pot block.
 */
public class BotanyPotBlock extends BaseEntityBlock {
    protected static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 10.0, 13.0);
    private final boolean isHopper;

    public BotanyPotBlock(BlockBehaviour.Properties properties, boolean isHopper) {
        super(properties);
        this.isHopper = isHopper;
    }

    public boolean isHopper() {
        return isHopper;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BotanyPotBlockEntity(pos, state, isHopper);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (lvl, pos, st, be) -> {
            if (be instanceof BotanyPotBlockEntity pot) {
                pot.serverTick(lvl, pos);
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BotanyPotBlockEntity pot)) {
            return InteractionResult.PASS;
        }

        // Empty hand: harvest/collect
        if (stack.isEmpty()) {
            if (!pot.getOutput().isEmpty()) {
                ItemStack harvested = pot.extractOutput();
                if (!player.getInventory().add(harvested)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, harvested);
                }
                player.sendOverlayMessage(Component.literal("Harvested " + harvested.getCount() + "x " + harvested.getHoverName().getString())
                        .withStyle(ChatFormatting.GREEN));
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                // Shift click removes crop then soil
                if (!pot.getCrop().isEmpty()) {
                    ItemStack crop = pot.removeCrop();
                    if (!player.getInventory().add(crop)) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, crop);
                    }
                    player.sendOverlayMessage(Component.literal("Removed crop").withStyle(ChatFormatting.YELLOW));
                    return InteractionResult.SUCCESS;
                } else if (!pot.getSoil().isEmpty()) {
                    ItemStack soil = pot.removeSoil();
                    if (!player.getInventory().add(soil)) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, soil);
                    }
                    player.sendOverlayMessage(Component.literal("Removed soil").withStyle(ChatFormatting.YELLOW));
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        // If soil empty and holding soil
        if (pot.getSoil().isEmpty() && BotanyPotsFeature.isValidSoil(stack)) {
            ItemStack soilStack = stack.copy();
            soilStack.setCount(1);
            pot.setSoil(soilStack);
            if (!player.isCreative()) stack.shrink(1);
            player.sendOverlayMessage(Component.literal("Placed soil: " + soilStack.getHoverName().getString())
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }

        // If soil present and crop empty and holding valid crop
        if (!pot.getSoil().isEmpty() && pot.getCrop().isEmpty() && BotanyPotsFeature.isValidCrop(stack)) {
            ItemStack cropStack = stack.copy();
            cropStack.setCount(1);
            pot.setCrop(cropStack);
            if (!player.isCreative()) stack.shrink(1);
            player.sendOverlayMessage(Component.literal("Planted: " + cropStack.getHoverName().getString())
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
