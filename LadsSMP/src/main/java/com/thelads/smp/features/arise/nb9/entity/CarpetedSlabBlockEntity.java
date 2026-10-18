package com.thelads.smp.features.arise.nb9.entity;

import com.thelads.smp.features.arise.nb9.AriseNB9Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Block entity for slabs with carpet fitted on top.
 */
public class CarpetedSlabBlockEntity extends BlockEntity {
    private String baseSlabBlockId = "minecraft:oak_slab";
    private String carpetItemId = "minecraft:white_carpet";

    public CarpetedSlabBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.CARPETED_SLAB_BE, pos, state);
    }

    public String getBaseSlabBlockId() {
        return baseSlabBlockId;
    }

    public void setBaseSlabBlockId(String baseSlabBlockId) {
        this.baseSlabBlockId = baseSlabBlockId != null ? baseSlabBlockId : "minecraft:oak_slab";
        setChanged();
    }

    public String getCarpetItemId() {
        return carpetItemId;
    }

    public void setCarpetItemId(String carpetItemId) {
        this.carpetItemId = carpetItemId != null ? carpetItemId : "minecraft:white_carpet";
        setChanged();
    }

    public ItemStack createSlabDrop() {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(baseSlabBlockId)).map(net.minecraft.core.Holder::value).orElse(Items.OAK_SLAB);
        return new ItemStack(item);
    }

    public ItemStack createCarpetDrop() {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(carpetItemId)).map(net.minecraft.core.Holder::value).orElse(Items.CARPET.white());
        return new ItemStack(item);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("BaseSlab", baseSlabBlockId);
        output.putString("CarpetItem", carpetItemId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.baseSlabBlockId = input.getStringOr("BaseSlab", "minecraft:oak_slab");
        this.carpetItemId = input.getStringOr("CarpetItem", "minecraft:white_carpet");
    }
}
