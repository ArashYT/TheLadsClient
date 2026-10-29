package com.thelads.smp.features.arise.nb3.snailmail;

import com.thelads.smp.features.arise.nb3.AriseNb3Mod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mailbox BlockEntity storing owner information and received mail stacks.
 */
public class MailboxBlockEntity extends BlockEntity {
    private UUID ownerUuid;
    private String ownerName = "Unclaimed";
    private final List<ItemStack> inbox = new ArrayList<>();

    public MailboxBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNb3Mod.MAILBOX_BE_TYPE, pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (ownerUuid != null) {
            output.putString("owner_uuid", ownerUuid.toString());
        }
        if (ownerName != null) {
            output.putString("owner_name", ownerName);
        }
        output.store("inbox", ItemStack.CODEC.listOf(), inbox);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.getString("owner_uuid").ifPresent(s -> {
            try {
                this.ownerUuid = UUID.fromString(s);
            } catch (Exception ignored) {}
        });
        input.getString("owner_name").ifPresent(name -> this.ownerName = name);
        this.inbox.clear();
        input.read("inbox", ItemStack.CODEC.listOf()).ifPresent(this.inbox::addAll);
        if (this.level != null && !this.level.isClientSide() && ownerUuid != null) {
            SnailMailFeature.registerMailboxIfAbsent(ownerUuid, this.level, this.worldPosition);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide() && ownerUuid != null) {
            SnailMailFeature.registerMailboxIfAbsent(ownerUuid, level, this.worldPosition);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.level.isClientSide()) {
            for (ItemStack item : inbox) {
                if (!item.isEmpty()) {
                    Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
                }
            }
            inbox.clear();
            if (ownerUuid != null) {
                SnailMailFeature.unregisterMailbox(ownerUuid, this.level, pos);
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    public void setOwner(UUID uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = (name != null) ? name : "Unknown";
        setChanged();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void depositMail(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            inbox.add(stack.copy());
            setChanged();
        }
    }

    public int getInboxMailCount() {
        return inbox.size();
    }

    public List<ItemStack> getInbox() {
        return inbox;
    }

    public void collectAllMail(Player player) {
        for (ItemStack item : inbox) {
            if (!item.isEmpty()) {
                if (!player.getInventory().add(item)) {
                    if (this.level != null) {
                        Containers.dropItemStack(this.level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, item);
                    }
                }
            }
        }
        int count = inbox.size();
        inbox.clear();
        setChanged();
        if (count > 0) {
            player.sendSystemMessage(Component.literal("Collected " + count + " items from mailbox!")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
