package com.thelads.smp.features.arise.nb10;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Base item for craftable hanging decorations (fairy lights, garlands, pennant bunting).
 * Manages two-point anchor selection, range checking, and connection instantiation.
 */
public class HangingDecorationItem extends Item {

    private final DecorationType type;
    private final int[] defaultColors;

    public HangingDecorationItem(Item.Properties properties, DecorationType type, int[] defaultColors) {
        super(properties);
        this.type = type != null ? type : DecorationType.LIGHTS;
        this.defaultColors = defaultColors != null ? defaultColors : new int[] { 0xFFE484 };
    }

    public DecorationType getType() {
        return type;
    }

    public int[] getColors(ItemStack stack) {
        return defaultColors;
    }

    private void sendFeedback(Player player, Component message) {
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(message, true);
        } else {
            player.sendSystemMessage(message);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos clickedPos = ctx.getClickedPos();
        Direction clickedFace = ctx.getClickedFace();
        ItemStack stack = ctx.getItemInHand();

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Determine the attachment anchor position and face
        BlockPos anchorPos;
        Direction anchorFace;
        BlockState clickedState = level.getBlockState(clickedPos);

        if (clickedState.is(AriseNB10Features.FASTENER_BLOCK)) {
            anchorPos = clickedPos;
            anchorFace = clickedState.getValue(FastenerBlock.FACING);
        } else if (clickedState.is(BlockTags.FENCES) || clickedState.is(BlockTags.WALLS)) {
            // For fences and walls: if clicked top and space above is air/replaceable, place on top
            BlockPos above = clickedPos.above();
            if (clickedFace == Direction.UP && (level.isEmptyBlock(above) || level.getBlockState(above).is(AriseNB10Features.FASTENER_BLOCK))) {
                anchorPos = above;
                anchorFace = Direction.UP;
            } else {
                BlockPos offset = clickedPos.relative(clickedFace);
                if (level.isEmptyBlock(offset) || level.getBlockState(offset).is(AriseNB10Features.FASTENER_BLOCK)) {
                    anchorPos = offset;
                    anchorFace = clickedFace;
                } else {
                    anchorPos = above;
                    anchorFace = Direction.UP;
                }
            }
        } else {
            // Solid block surface: place in adjacent block against the clicked face
            BlockPos offset = clickedPos.relative(clickedFace);
            if (level.isEmptyBlock(offset) || level.getBlockState(offset).is(AriseNB10Features.FASTENER_BLOCK)) {
                anchorPos = offset;
                anchorFace = clickedFace;
            } else {
                anchorPos = clickedPos;
                anchorFace = clickedFace;
            }
        }

        ConnectionTracker.PendingConnection pending = ConnectionTracker.getPending(player);

        if (pending == null) {
            // First click: anchor start point
            ensureFastener(level, anchorPos, anchorFace);
            ConnectionTracker.setPending(player, anchorPos, anchorFace, stack);
            level.playSound(null, anchorPos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 1.0F, 1.2F);
            sendFeedback(player, Component.literal("§eAttached start to [" + anchorPos.getX() + ", " + anchorPos.getY() + ", " + anchorPos.getZ() + "]. Right-click second block to connect."));
            return InteractionResult.SUCCESS;
        } else {
            // Second click: anchor end point and complete connection
            if (!pending.dimension().equals(level.dimension())) {
                sendFeedback(player, Component.literal("§cCannot connect across dimensions! Start anchor cancelled."));
                ConnectionTracker.clearPending(player);
                return InteractionResult.FAIL;
            }

            BlockPos startPos = pending.pos();
            if (startPos.equals(anchorPos)) {
                sendFeedback(player, Component.literal("§7Connection cancelled (same attachment point)."));
                ConnectionTracker.clearPending(player);
                return InteractionResult.SUCCESS;
            }

            double distSq = startPos.distSqr(anchorPos);
            int maxDist = AriseNB10Config.get().maxConnectionDistance;
            if (distSq > (double) maxDist * maxDist) {
                sendFeedback(player, Component.literal("§cToo far! Maximum connection distance is " + maxDist + " blocks."));
                return InteractionResult.FAIL;
            }

            // Ensure fastener exists at both ends
            ensureFastener(level, startPos, pending.face());
            ensureFastener(level, anchorPos, anchorFace);

            BlockEntity startBe = level.getBlockEntity(startPos);
            if (startBe instanceof FastenerBlockEntity fastenerBe) {
                HangingConnection connection = new HangingConnection(
                        UUID.randomUUID(),
                        anchorPos,
                        anchorFace,
                        getType(),
                        getColors(stack),
                        AriseNB10Config.get().sagMultiplier,
                        stack.copyWithCount(1)
                );
                fastenerBe.addConnection(connection);

                if (!player.hasInfiniteMaterials()) {
                    stack.shrink(1);
                }

                level.playSound(null, anchorPos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.1F);
                ConnectionTracker.clearPending(player);
                sendFeedback(player, Component.literal("§aHanging connection established!"));
                return InteractionResult.SUCCESS;
            } else {
                sendFeedback(player, Component.literal("§cFailed to locate start fastener block entity."));
                ConnectionTracker.clearPending(player);
                return InteractionResult.FAIL;
            }
        }
    }

    private void ensureFastener(Level level, BlockPos pos, Direction face) {
        BlockState current = level.getBlockState(pos);
        if (!current.is(AriseNB10Features.FASTENER_BLOCK)) {
            level.setBlock(pos, AriseNB10Features.FASTENER_BLOCK.defaultBlockState().setValue(FastenerBlock.FACING, face), Block.UPDATE_ALL);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (ConnectionTracker.getPending(player) != null) {
                ConnectionTracker.clearPending(player);
                if (!level.isClientSide()) {
                    sendFeedback(player, Component.literal("§7Connection cancelled."));
                    player.playSound(SoundEvents.DISPENSER_FAIL, 1.0F, 1.5F);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.use(level, player, hand);
    }
}
