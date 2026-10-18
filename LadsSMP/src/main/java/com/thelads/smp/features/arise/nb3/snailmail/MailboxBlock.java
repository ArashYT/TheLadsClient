package com.thelads.smp.features.arise.nb3.snailmail;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Mailbox Block for Snail Mail.
 * Interacting opens the mailbox if player is owner or permitted.
 */
public class MailboxBlock extends Block implements EntityBlock {
    public MailboxBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MailboxBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MailboxBlockEntity mailbox) {
                mailbox.setOwner(player.getUUID(), player.getName().getString());
                SnailMailFeature.registerMailbox(player.getUUID(), level, pos);
                player.sendSystemMessage(Component.literal("Mailbox registered to " + player.getName().getString())
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MailboxBlockEntity mailbox) {
                for (ItemStack item : mailbox.getInbox()) {
                    if (!item.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
                    }
                }
                mailbox.getInbox().clear();
                SnailMailFeature.unregisterMailbox(mailbox.getOwnerUuid());
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (AriseNb3Config.get().snailMail.protectBoxDestroy && level.getBlockEntity(pos) instanceof MailboxBlockEntity mailbox) {
            boolean isAdmin = player.permissions().hasPermission(Permissions.COMMANDS_ADMIN)
                    || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
            int opLevel = isAdmin ? 4 : 0;
            if (!SnailMailFeature.canAccessMailbox(player, mailbox.getOwnerUuid(), opLevel)) {
                return 0.0f; // Indestructible for non-owners when break protection is enabled
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MailboxBlockEntity mailbox)) {
            return InteractionResult.PASS;
        }

        boolean isAdmin = player.permissions().hasPermission(Permissions.COMMANDS_ADMIN)
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        int opLevel = isAdmin ? 4 : 0;
        if (!SnailMailFeature.canAccessMailbox(player, mailbox.getOwnerUuid(), opLevel)) {
            player.sendSystemMessage(Component.literal("This mailbox is locked to " + mailbox.getOwnerName())
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // Display inbox summary
        int mailCount = mailbox.getInboxMailCount();
        player.sendSystemMessage(Component.literal("--- Mailbox (" + mailbox.getOwnerName() + ") ---")
                .withStyle(ChatFormatting.GOLD));
        if (mailCount == 0) {
            player.sendSystemMessage(Component.literal("Your inbox is empty.")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("You have " + mailCount + " uncollected letters/packages.")
                    .withStyle(ChatFormatting.YELLOW));
            // Collect mail into player inventory
            mailbox.collectAllMail(player);
        }

        return InteractionResult.SUCCESS;
    }
}
