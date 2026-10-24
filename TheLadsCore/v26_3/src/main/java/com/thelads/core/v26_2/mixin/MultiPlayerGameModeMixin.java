package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.DropConfirmationHelper;
import com.thelads.core.client.InventoryTweaks;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.DropConfirmationModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V", at = @At("HEAD"), cancellable = true)
    private void lads$dropConfirm(LocalPlayer player, boolean entireStack, CallbackInfo ci) {
        if (player == null) return;
        if (InventoryTweaks.locked(player.getInventory().getSelectedSlot())) { // Slot Lock: nothing dropped, nothing sent
            ci.cancel();
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            Module m = ModuleManager.getInstance().getModule(DropConfirmationModule.NAME);
            if (m instanceof DropConfirmationModule dcm && dcm.isEnabled()) {
                boolean isEnchanted = held.isEnchanted();
                boolean isValuable = isEnchanted || (!dcm.isEnchantOnly() && DropConfirmationHelper.isValuableName(held.getItem().getDescriptionId()));
                if (!DropConfirmationHelper.checkCanDrop(System.currentTimeMillis(), dcm.getTimeoutMillis(), isValuable, true)) {
                    Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("\u00a7cPress drop key again to confirm!"), false);
                    ci.cancel();
                }
            }
        }
    }

    /**
     * Slot Lock: every container click the client makes (vanilla's, Mouse Tweaks', Armor Swap's) passes here; one that would move a
     * locked item out of the player's inventory is dropped before anything is sent. A double-click gathering items of a kind skips
     * it when a locked slot holds that kind.
     */
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$slotLock(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (!InventoryTweaks.lockOn) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId) return;
        Slot slot = slotId >= 0 && slotId < menu.slots.size() ? menu.slots.get(slotId) : null;
        int inventorySlot = slot != null && slot.container instanceof Inventory ? slot.getContainerSlot() : -1;
        if (InventoryTweaks.blocks(inventorySlot, input.ordinal(), button)) {
            ci.cancel();
            return;
        }
        if (input != ContainerInput.PICKUP_ALL) return;
        ItemStack carried = menu.getCarried();
        for (Slot other : menu.slots)
            if (other.container instanceof Inventory && InventoryTweaks.locked(other.getContainerSlot())
                && ItemStack.isSameItemSameComponents(other.getItem(), carried)) {
                ci.cancel();
                return;
            }
    }
}
