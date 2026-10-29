package com.thelads.smp.features.arise.nb3.botanypots;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import com.thelads.smp.features.arise.nb3.AriseNb3Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * BlockEntity for Botany Pots:
 * Holds soil, crop, growth progress timer, and output items.
 * If isHopper is true, pushes output items downward into attached container.
 */
public class BotanyPotBlockEntity extends BlockEntity {
    private final boolean isHopper;
    private ItemStack soil = ItemStack.EMPTY;
    private ItemStack crop = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private int growthTicks = 0;
    private int exportCooldown = 0;

    public BotanyPotBlockEntity(BlockPos pos, BlockState state, boolean isHopper) {
        super(isHopper ? AriseNb3Mod.HOPPER_BOTANY_POT_BE_TYPE : AriseNb3Mod.BOTANY_POT_BE_TYPE, pos, state);
        this.isHopper = isHopper;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("soil", ItemStack.CODEC, this.soil);
        output.store("crop", ItemStack.CODEC, this.crop);
        output.store("output", ItemStack.CODEC, this.output);
        output.putInt("growth_ticks", this.growthTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.soil = input.read("soil", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.crop = input.read("crop", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.output = input.read("output", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        this.growthTicks = input.getIntOr("growth_ticks", 0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null && !this.level.isClientSide()) {
            if (!this.soil.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.soil);
                this.soil = ItemStack.EMPTY;
            }
            if (!this.crop.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.crop);
                this.crop = ItemStack.EMPTY;
            }
            if (!this.output.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.output);
                this.output = ItemStack.EMPTY;
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    public void setSoil(ItemStack s) {
        this.soil = s;
        setChanged();
    }

    public ItemStack getSoil() {
        return soil;
    }

    public ItemStack removeSoil() {
        ItemStack old = this.soil;
        this.soil = ItemStack.EMPTY;
        setChanged();
        return old;
    }

    public void setCrop(ItemStack c) {
        this.crop = c;
        this.growthTicks = 0;
        setChanged();
    }

    public ItemStack getCrop() {
        return crop;
    }

    public ItemStack removeCrop() {
        ItemStack old = this.crop;
        this.crop = ItemStack.EMPTY;
        this.growthTicks = 0;
        setChanged();
        return old;
    }

    public ItemStack getOutput() {
        return output;
    }

    public ItemStack extractOutput() {
        ItemStack out = this.output;
        this.output = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    public int getGrowthTicks() {
        return growthTicks;
    }

    public void setGrowthTicksForTesting(int ticks) {
        this.growthTicks = ticks;
    }

    public float getGrowthPercent() {
        int max = AriseNb3Config.get().botanyPots.growthIntervalTicks;
        return max <= 0 ? 1.0f : Math.min(1.0f, (float) growthTicks / (float) max);
    }

    public void serverTick(Level level, BlockPos pos) {
        if (!AriseNb3Config.get().botanyPots.enabled) return;

        // If soil and crop are planted and output is empty or matchable
        if (!soil.isEmpty() && !crop.isEmpty()) {
            growthTicks++;
            int maxTicks = AriseNb3Config.get().botanyPots.growthIntervalTicks;
            if (growthTicks >= maxTicks) {
                growthTicks = 0;
                ItemStack yield = BotanyPotsFeature.getYield(crop);
                if (!yield.isEmpty()) {
                    if (output.isEmpty()) {
                        output = yield;
                        setChanged();
                    } else if (ItemStack.isSameItemSameComponents(output, yield)
                            && output.getCount() + yield.getCount() <= output.getMaxStackSize()) {
                        output.grow(yield.getCount());
                        setChanged();
                    }
                }
            }
        }

        // Hopper Pot auto-export
        if (isHopper && AriseNb3Config.get().botanyPots.enableHopperPots && !output.isEmpty()) {
            if (++exportCooldown >= AriseNb3Config.get().botanyPots.hopperExportIntervalTicks) {
                exportCooldown = 0;
                exportToContainerBelow(level, pos);
            }
        }
    }

    public boolean exportToContainerBelow(Level level, BlockPos pos) {
        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slotStack = container.getItem(i);
                if (slotStack.isEmpty()) {
                    container.setItem(i, output.copy());
                    output = ItemStack.EMPTY;
                    setChanged();
                    return true;
                } else if (ItemStack.isSameItemSameComponents(slotStack, output)
                        && slotStack.getCount() + output.getCount() <= slotStack.getMaxStackSize()) {
                    slotStack.grow(output.getCount());
                    output = ItemStack.EMPTY;
                    setChanged();
                    return true;
                }
            }
        }
        return false;
    }
}
