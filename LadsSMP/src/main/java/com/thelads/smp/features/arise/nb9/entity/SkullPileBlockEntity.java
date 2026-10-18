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
 * Block entity for stacked skull piles.
 * Tracks both the bottom and top skull items to guarantee zero item loss on break.
 */
public class SkullPileBlockEntity extends BlockEntity {
    private String bottomSkullId = "minecraft:skeleton_skull";
    private String topSkullId = "minecraft:skeleton_skull";

    public SkullPileBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.SKULL_PILE_BE, pos, state);
    }

    public String getBottomSkullId() {
        return bottomSkullId;
    }

    public void setBottomSkullId(String bottomSkullId) {
        this.bottomSkullId = bottomSkullId != null ? bottomSkullId : "minecraft:skeleton_skull";
        setChanged();
    }

    public String getTopSkullId() {
        return topSkullId;
    }

    public void setTopSkullId(String topSkullId) {
        this.topSkullId = topSkullId != null ? topSkullId : "minecraft:skeleton_skull";
        setChanged();
    }

    public ItemStack createBottomDrop() {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(bottomSkullId)).map(net.minecraft.core.Holder::value).orElse(Items.SKELETON_SKULL);
        return new ItemStack(item);
    }

    public ItemStack createTopDrop() {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(topSkullId)).map(net.minecraft.core.Holder::value).orElse(Items.SKELETON_SKULL);
        return new ItemStack(item);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("BottomSkull", bottomSkullId);
        output.putString("TopSkull", topSkullId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.bottomSkullId = input.getStringOr("BottomSkull", "minecraft:skeleton_skull");
        this.topSkullId = input.getStringOr("TopSkull", "minecraft:skeleton_skull");
    }
}
