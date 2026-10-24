package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.client.InventoryTweaks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Slot Lock on 1.20.1: every container click the client makes (vanilla's, Mouse Tweaks', Armor Swap's) passes here; one that would
 * move a locked item out of the player's inventory is dropped before anything is sent. A double-click gathering items of a kind
 * skips it when a locked slot holds that kind.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class SlotLockGameModeMixin1201 {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$slotLock(int containerId, int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        if (!InventoryTweaks.lockOn) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId) return;
        Slot slot = slotId >= 0 && slotId < menu.slots.size() ? menu.slots.get(slotId) : null;
        int inventorySlot = slot != null && slot.container instanceof Inventory ? slot.getContainerSlot() : -1;
        if (InventoryTweaks.blocks(inventorySlot, type.ordinal(), button)) {
            ci.cancel();
            return;
        }
        if (type != ClickType.PICKUP_ALL) return;
        ItemStack carried = menu.getCarried();
        for (Slot other : menu.slots)
            if (other.container instanceof Inventory && InventoryTweaks.locked(other.getContainerSlot())
                && ItemStack.isSameItemSameTags(other.getItem(), carried)) {
                ci.cancel();
                return;
            }
    }
}
