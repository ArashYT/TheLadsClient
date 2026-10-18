package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * Storage for a player's 4 cosmetic armor slots and their visibility toggles.
 */
public final class CosmeticArmorData {
    private final ItemStack[] slots = new ItemStack[4];
    private final boolean[] visible = new boolean[4];

    public CosmeticArmorData() {
        Arrays.fill(slots, ItemStack.EMPTY);
        Arrays.fill(visible, true);
    }

    private static int slotToIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> -1;
        };
    }

    public ItemStack get(EquipmentSlot slot) {
        int idx = slotToIndex(slot);
        return (idx >= 0 && idx < 4 && slots[idx] != null) ? slots[idx] : ItemStack.EMPTY;
    }

    public void set(EquipmentSlot slot, ItemStack stack) {
        int idx = slotToIndex(slot);
        if (idx >= 0 && idx < 4) {
            slots[idx] = (stack == null) ? ItemStack.EMPTY : stack.copy();
        }
    }

    public boolean isVisible(EquipmentSlot slot) {
        int idx = slotToIndex(slot);
        return idx < 0 || idx >= 4 || visible[idx];
    }

    public void setVisible(EquipmentSlot slot, boolean isVisible) {
        int idx = slotToIndex(slot);
        if (idx >= 0 && idx < 4) {
            visible[idx] = isVisible;
        }
    }

    public void setVisibility(EquipmentSlot slot, boolean isVisible) {
        setVisible(slot, isVisible);
    }

    public boolean toggleVisibility(EquipmentSlot slot) {
        int idx = slotToIndex(slot);
        if (idx >= 0 && idx < 4) {
            visible[idx] = !visible[idx];
            return visible[idx];
        }
        return true;
    }

    public void clear() {
        Arrays.fill(slots, ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        for (ItemStack s : slots) {
            if (s != null && !s.isEmpty()) return false;
        }
        return true;
    }
}
