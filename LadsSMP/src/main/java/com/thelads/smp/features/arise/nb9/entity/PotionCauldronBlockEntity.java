package com.thelads.smp.features.arise.nb9.entity;

import com.thelads.smp.features.arise.nb9.AriseNB9Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for cauldrons holding brewed and mixed potions.
 * Persists PotionContents and remaining dipped arrow crafting uses per level.
 */
public class PotionCauldronBlockEntity extends BlockEntity {
    private PotionContents potionContents = PotionContents.EMPTY;
    private int usesLeft = 2;

    public PotionCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.POTION_CAULDRON_BE, pos, state);
    }

    public PotionContents getPotionContents() {
        return potionContents;
    }

    public void setPotionContents(PotionContents potionContents) {
        this.potionContents = potionContents != null ? potionContents : PotionContents.EMPTY;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int getUsesLeft() {
        return usesLeft;
    }

    public void setUsesLeft(int usesLeft) {
        this.usesLeft = usesLeft;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (potionContents != null && potionContents != PotionContents.EMPTY) {
            output.store("PotionContents", PotionContents.CODEC, potionContents);
        }
        output.putInt("UsesLeft", usesLeft);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.potionContents = input.read("PotionContents", PotionContents.CODEC).orElse(PotionContents.EMPTY);
        this.usesLeft = input.getIntOr("UsesLeft", 2);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        tag.putInt("Color", potionContents != null ? potionContents.getColor() : 0x385DC6);
        tag.putInt("UsesLeft", usesLeft);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
