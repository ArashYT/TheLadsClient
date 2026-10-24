package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.InventoryTweaks;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slot Lock on 1.8.9: every container click the client makes (vanilla's, Mouse Tweaks', Armor Swap's) passes here; one that would
 * move a locked item out of the player's inventory is dropped before anything is sent. A double-click gathering items of a kind
 * skips it when a locked slot holds that kind.
 */
@Mixin(PlayerControllerMP.class)
public abstract class SlotLockControllerMixin189 {
    @Inject(method = "windowClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSlotLock(int windowId, int slotId, int button, int mode, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (!InventoryTweaks.lockOn) return;
        Container menu = player.openContainer;
        if (menu == null || menu.windowId != windowId) return;
        Slot slot = slotId >= 0 && slotId < menu.inventorySlots.size() ? menu.inventorySlots.get(slotId) : null;
        int inventorySlot = slot != null && slot.inventory instanceof InventoryPlayer ? slot.getSlotIndex() : -1;
        if (InventoryTweaks.blocks(inventorySlot, mode, button)) {
            cir.setReturnValue(null);
            return;
        }
        if (mode != InventoryTweaks.PICKUP_ALL) return;
        ItemStack carried = player.inventory.getItemStack();
        for (Slot other : menu.inventorySlots)
            if (other.inventory instanceof InventoryPlayer && InventoryTweaks.locked(other.getSlotIndex()) && carried != null
                && other.getStack() != null && other.getStack().isItemEqual(carried) && ItemStack.areItemStackTagsEqual(other.getStack(), carried)) {
                cir.setReturnValue(null);
                return;
            }
    }
}
