package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 4: Prevents Hoppers and automation from sucking up or transferring disabled items.
 */
@Mixin(HopperBlockEntity.class)
public abstract class MixinHopperBlockEntity {

    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ladssmp$blockHopperTransfer(
            Container source, Container destination, ItemStack stack, Direction direction, CallbackInfoReturnable<ItemStack> cir) {
        if (ItemDisablerSystem.isDisabled(stack)) {
            ItemDisablerSystem.writeAuditLog("Blocked hopper transfer of disabled item " + ItemDisablerSystem.getItemIdString(stack));
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(
            method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void ladssmp$blockHopperEntityPickup(
            Container destination, ItemEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && ItemDisablerSystem.isDisabled(entity.getItem())) {
            entity.discard();
            cir.setReturnValue(false);
        }
    }
}
