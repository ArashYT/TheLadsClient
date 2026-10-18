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
 * Block entity for mob skulls holding candles.
 * Tracks the base skull type and candle item type to guarantee proper drop recovery without item loss.
 */
public class SkullCandleBlockEntity extends BlockEntity {
    private String skullItemId = "minecraft:skeleton_skull";
    private String candleItemId = "minecraft:candle";

    public SkullCandleBlockEntity(BlockPos pos, BlockState state) {
        super(AriseNB9Features.SKULL_CANDLE_BE, pos, state);
    }

    public String getSkullItemId() {
        return skullItemId;
    }

    public void setSkullItemId(String skullItemId) {
        this.skullItemId = skullItemId != null ? skullItemId : "minecraft:skeleton_skull";
        setChanged();
    }

    public String getCandleItemId() {
        return candleItemId;
    }

    public void setCandleItemId(String candleItemId) {
        this.candleItemId = candleItemId != null ? candleItemId : "minecraft:candle";
        setChanged();
    }

    public ItemStack createSkullDrop() {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(skullItemId)).map(net.minecraft.core.Holder::value).orElse(Items.SKELETON_SKULL);
        return new ItemStack(item);
    }

    public ItemStack createCandleDrop(int count) {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(candleItemId)).map(net.minecraft.core.Holder::value).orElse(Items.CANDLE);
        return new ItemStack(item, count);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("SkullItem", skullItemId);
        output.putString("CandleItem", candleItemId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.skullItemId = input.getStringOr("SkullItem", "minecraft:skeleton_skull");
        this.candleItemId = input.getStringOr("CandleItem", "minecraft:candle");
    }
}
