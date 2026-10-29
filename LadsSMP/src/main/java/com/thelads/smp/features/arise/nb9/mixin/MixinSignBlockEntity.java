package com.thelads.smp.features.arise.nb9.mixin;

import com.thelads.smp.features.arise.nb9.HangingSignItemHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin on SignBlockEntity to store and persist items displayed on hanging signs into world chunk storage.
 */
@Mixin(SignBlockEntity.class)
public abstract class MixinSignBlockEntity extends BlockEntity implements HangingSignItemHolder {

    @Unique
    private ItemStack ladssmp$displayedItem = ItemStack.EMPTY;

    protected MixinSignBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public ItemStack ladssmp$getDisplayedItem() {
        return ladssmp$displayedItem != null ? ladssmp$displayedItem : ItemStack.EMPTY;
    }

    @Override
    public void ladssmp$setDisplayedItem(ItemStack stack) {
        this.ladssmp$displayedItem = (stack != null && !stack.isEmpty()) ? stack.copy() : ItemStack.EMPTY;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void ladssmp$saveDisplayedItem(ValueOutput output, CallbackInfo ci) {
        if (!ladssmp$displayedItem.isEmpty()) {
            output.store("LadssmpDisplayedItem", ItemStack.CODEC, ladssmp$displayedItem);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void ladssmp$loadDisplayedItem(ValueInput input, CallbackInfo ci) {
        this.ladssmp$displayedItem = input.read("LadssmpDisplayedItem", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide() && ladssmp$displayedItem != null && !ladssmp$displayedItem.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, pos, ladssmp$displayedItem);
            ladssmp$displayedItem = ItemStack.EMPTY;
        }
    }
}
