package com.thelads.smp.features.arise.nb9.entity;

import com.thelads.smp.features.arise.nb9.AriseNB9Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for tripwire hooks acting as tool racks.
 * Safely holds and persists a tool or weapon item stack.
 */
public class ToolHookBlockEntity extends BlockEntity {
    private ItemStack toolStack = ItemStack.EMPTY;

    public ToolHookBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.TOOL_HOOK_BE, pos, state);
    }

    public ItemStack getToolStack() {
        return toolStack;
    }

    public void setToolStack(ItemStack toolStack) {
        this.toolStack = toolStack != null ? toolStack : ItemStack.EMPTY;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!toolStack.isEmpty()) {
            output.store("ToolStack", ItemStack.CODEC, toolStack);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.toolStack = input.read("ToolStack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return super.getUpdateTag(lookup);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
