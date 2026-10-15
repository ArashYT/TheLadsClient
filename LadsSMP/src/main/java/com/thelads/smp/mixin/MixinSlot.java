package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 4: Prevents placing disabled items into container slots or picking them up from slots.
 */
@Mixin(Slot.class)
public abstract class MixinSlot {
    @Shadow
    @Final
    public Container container;

    @Shadow
    public abstract ItemStack getItem();

    @Shadow
    public abstract void set(ItemStack stack);

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void ladssmp$blockDisabledMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ItemDisablerSystem.isDisabled(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void ladssmp$blockDisabledMayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        ItemStack current = this.getItem();
        if (ItemDisablerSystem.isDisabled(current) && !ItemDisablerSystem.shouldBypass(player)) {
            this.set(ItemStack.EMPTY);
            cir.setReturnValue(false);
        }
    }
}
