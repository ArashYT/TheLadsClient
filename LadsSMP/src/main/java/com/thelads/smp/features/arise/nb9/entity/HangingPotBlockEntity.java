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
 * Block entity for suspended hanging flower pots.
 */
public class HangingPotBlockEntity extends BlockEntity {
    private String plantItemId = "minecraft:air";

    public HangingPotBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.HANGING_POT_BE, pos, state);
    }

    public String getPlantItemId() {
        return plantItemId;
    }

    public void setPlantItemId(String plantItemId) {
        this.plantItemId = plantItemId != null ? plantItemId : "minecraft:air";
        setChanged();
    }

    public ItemStack createPlantDrop() {
        if ("minecraft:air".equals(plantItemId)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(plantItemId)).map(net.minecraft.core.Holder::value).orElse(Items.AIR);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("PlantItem", plantItemId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.plantItemId = input.getStringOr("PlantItem", "minecraft:air");
    }
}
