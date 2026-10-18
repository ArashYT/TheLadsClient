package com.thelads.smp.features.arise.nb3.backweapon;

import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Back Weapon Slot Feature:
 * Displays the player's sheathed primary weapon/tool on their back model when unequipped,
 * and allows instant swapping with the active mainhand slot.
 */
public final class BackWeaponFeature {
    private BackWeaponFeature() {}

    public static boolean isWeaponOrTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // In 26.3, weapons and tools are defined by their DataComponents
        if (stack.has(DataComponents.WEAPON)
                || stack.has(DataComponents.TOOL)
                || stack.has(DataComponents.PIERCING_WEAPON)
                || stack.has(DataComponents.KINETIC_WEAPON)
                || stack.has(DataComponents.DAMAGE_RESISTANT)) {
            return true;
        }
        // Also check if damageable item that is not armor (e.g. tools/weapons with max damage)
        return stack.has(DataComponents.MAX_DAMAGE) && !stack.has(DataComponents.EQUIPPABLE);
    }

    /**
     * Resolves the current sheathed weapon to display on the player's back.
     * Searches hotbar slots for the primary weapon not currently held in the main hand.
     */
    public static ItemStack getBackWeaponStack(Player player) {
        if (!AriseNb3Config.get().backWeapon.enabled || !AriseNb3Config.get().backWeapon.showBackWeapon) {
            return ItemStack.EMPTY;
        }
        if (player == null) return ItemStack.EMPTY;

        Inventory inv = player.getInventory();
        int activeSlot = inv.getSelectedSlot();
        ItemStack held = inv.getItem(activeSlot);

        // Find the first weapon in hotbar slots that isn't currently held
        for (int i = 0; i < 9; i++) {
            if (i == activeSlot) continue;
            ItemStack candidate = inv.getItem(i);
            if (isWeaponOrTool(candidate)) {
                return candidate;
            }
        }

        // If held is not a weapon, check hotbar
        if (!isWeaponOrTool(held)) {
            for (int i = 0; i < 9; i++) {
                ItemStack candidate = inv.getItem(i);
                if (isWeaponOrTool(candidate)) return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Swaps the active mainhand slot with the sheathed back weapon.
     */
    public static boolean swapBackWeapon(Player player) {
        if (!AriseNb3Config.get().backWeapon.enabled || !AriseNb3Config.get().backWeapon.allowSwapping) {
            return false;
        }
        if (player == null) return false;

        Inventory inv = player.getInventory();
        int activeSlot = inv.getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            if (i == activeSlot) continue;
            ItemStack candidate = inv.getItem(i);
            if (isWeaponOrTool(candidate)) {
                ItemStack current = inv.getItem(activeSlot);
                inv.setItem(activeSlot, candidate);
                inv.setItem(i, current);
                return true;
            }
        }
        return false;
    }
}
