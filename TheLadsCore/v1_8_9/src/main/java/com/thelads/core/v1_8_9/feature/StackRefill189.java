package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.modules.StackRefillModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * StackRefill on 1.8.9, as 26.x NativeStackRefill: when the held hotbar stack runs out (or the held tool breaks), the matching
 * stack from the main inventory (slots 9..35, last first) is swapped in with the inventory's hotbar-swap click (window click
 * mode 2), the same click 26.x sends. 1.8.9 has no off hand: Refill offhand does nothing here. Off on Hypixel.
 */
public final class StackRefill189 {
    private static int lastSlot = -1;
    private static ItemStack lastStack;

    private StackRefill189() {}

    public static void tick(Minecraft mc) {
        StackRefillModule mod = StackRefillModule.get();
        EntityPlayerSP player = mc.thePlayer;
        if (mod == null || !mod.isEnabled() || HypixelSafetyManager.isHypixel() || player == null || !player.isEntityAlive()
                || player.capabilities.isCreativeMode || player.isSpectator() || mc.currentScreen != null || mc.playerController == null) {
            lastSlot = -1;
            lastStack = null;
            return;
        }
        InventoryPlayer inv = player.inventory;
        ItemStack current = inv.getCurrentItem();
        if (inv.currentItem == lastSlot && lastStack != null && current == null
                && (lastStack.isItemStackDamageable() ? mod.canReplaceBrokenTools() : mod.canRefillConsumables())) {
            int match = match(inv, lastStack);
            if (match != -1) {
                mc.playerController.windowClick(player.inventoryContainer.windowId, match, inv.currentItem, 2, player);
                current = inv.getCurrentItem();
            }
        }
        lastSlot = inv.currentItem;
        lastStack = current == null ? null : current.copy();
    }

    /** Main inventory slots 9..35 are the player container's slots of the same number. */
    static int match(InventoryPlayer inv, ItemStack previous) {
        for (int slot = 35; slot >= 9; slot--) {
            ItemStack candidate = inv.mainInventory[slot];
            // Metadata tells 1.8.9's potions, wool colours and the like apart (26.x: potion contents); tools differ only in damage.
            if (candidate != null && candidate.getItem() == previous.getItem()
                    && (!previous.getHasSubtypes() || candidate.getMetadata() == previous.getMetadata())) return slot;
        }
        return -1;
    }
}
