package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Storage for a player's 4 cosmetic armor slots and their visibility toggles.
 * Server side the slots hold real items moved out of the player's inventory (saved with the player);
 * client side this is only the render cache filled from server syncs.
 */
public final class CosmeticArmorData {
    /** Per-slot lenient fields: an item whose mod was removed loads as empty instead of failing the whole record. */
    public static final Codec<CosmeticArmorData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.lenientOptionalFieldOf("head").forGetter(d -> d.slots[0]),
            ItemStack.lenientOptionalFieldOf("chest").forGetter(d -> d.slots[1]),
            ItemStack.lenientOptionalFieldOf("legs").forGetter(d -> d.slots[2]),
            ItemStack.lenientOptionalFieldOf("feet").forGetter(d -> d.slots[3]),
            Codec.BOOL.listOf().optionalFieldOf("visible", List.of()).forGetter(d -> List.of(d.visible[0], d.visible[1], d.visible[2], d.visible[3]))
    ).apply(i, CosmeticArmorData::new));

    private final ItemStack[] slots = new ItemStack[4];
    private final boolean[] visible = new boolean[4];

    public CosmeticArmorData() {
        Arrays.fill(slots, ItemStack.EMPTY);
        Arrays.fill(visible, true);
    }

    private CosmeticArmorData(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet, List<Boolean> vis) {
        this();
        slots[0] = head;
        slots[1] = chest;
        slots[2] = legs;
        slots[3] = feet;
        for (int i = 0; i < Math.min(4, vis.size()); i++) visible[i] = vis.get(i);
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
