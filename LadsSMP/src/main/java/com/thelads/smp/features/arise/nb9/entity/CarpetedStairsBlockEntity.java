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
 * Block entity for stairs carpeted with wool rugs.
 * Stores base stair block and carpet item identifiers to restore full drops without item loss.
 */
public class CarpetedStairsBlockEntity extends BlockEntity {
    private String baseStairBlockId = "minecraft:oak_stairs";
    private String carpetItemId = "minecraft:white_carpet";

    public CarpetedStairsBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.CARPETED_STAIRS_BE, pos, state);
    }

    public String getBaseStairBlockId() {
        return baseStairBlockId;
    }

    public void setBaseStairBlockId(String baseStairBlockId) {
        this.baseStairBlockId = baseStairBlockId != null ? baseStairBlockId : "minecraft:oak_stairs";
        setChanged();
    }

    public String getCarpetItemId() {
        return carpetItemId;
    }

    public void setCarpetItemId(String carpetItemId) {
        this.carpetItemId = carpetItemId != null ? carpetItemId : "minecraft:white_carpet";
        setChanged();
    }

    public ItemStack createStairDrop() {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(baseStairBlockId)).map(net.minecraft.core.Holder::value).orElse(Items.OAK_STAIRS);
        return new ItemStack(item);
    }

    public ItemStack createCarpetDrop() {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(carpetItemId)).map(net.minecraft.core.Holder::value).orElse(Items.CARPET.white());
        return new ItemStack(item);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("BaseStair", baseStairBlockId);
        output.putString("CarpetItem", carpetItemId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.baseStairBlockId = input.getStringOr("BaseStair", "minecraft:oak_stairs");
        this.carpetItemId = input.getStringOr("CarpetItem", "minecraft:white_carpet");
    }
}
