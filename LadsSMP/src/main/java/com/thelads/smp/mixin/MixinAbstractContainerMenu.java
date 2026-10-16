package com.thelads.smp.mixin;

import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pathway 4: Intercepts container clicks, cursor carries, and shift-click transfers of disabled items.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract void setCarried(ItemStack stack);

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void ladssmp$onMenuClicked(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (ItemDisablerSystem.shouldBypass(player)) return;

        ItemStack carried = this.getCarried();
        if (ItemDisablerSystem.isDisabled(carried)) {
            ServerPlayer sp = player instanceof ServerPlayer s ? s : null;
            this.setCarried(ItemDisablerSystem.handleDisabledStackFound(sp, carried, "menu_carried_click"));
            ci.cancel();
            return;
        }

        if (slotId >= 0 && slotId < this.slots.size()) {
            Slot slot = this.slots.get(slotId);
            if (slot != null) {
                ItemStack inSlot = slot.getItem();
                if (ItemDisablerSystem.isDisabled(inSlot)) {
                    ServerPlayer sp = player instanceof ServerPlayer s ? s : null;
                    slot.set(ItemDisablerSystem.handleDisabledStackFound(sp, inSlot, "menu_slot_" + slotId));
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void ladssmp$onMoveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection, CallbackInfoReturnable<Boolean> cir) {
        if (ItemDisablerSystem.isDisabled(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "clearContainer(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/Container;)V", at = @At("HEAD"))
    private void ladssmp$keepStickyLapisInTable(Player player, net.minecraft.world.Container container, CallbackInfo ci) {
        if (!com.thelads.smp.config.LadsConfig.get().features.stickyEnchantingLapis) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (self instanceof net.minecraft.world.inventory.EnchantmentMenu
                && self instanceof com.thelads.smp.features.EnchantmentMenuSlotsAccessor accessor) {
            accessor.ladssmp$getEnchantSlots().setItem(1, ItemStack.EMPTY);
        }
    }
}
