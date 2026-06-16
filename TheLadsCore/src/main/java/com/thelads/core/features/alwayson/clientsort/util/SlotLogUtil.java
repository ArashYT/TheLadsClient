/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.Slot
 */
package com.thelads.core.features.alwayson.clientsort.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public class SlotLogUtil {
    public static String listSlotIndexes(Iterable<Slot> slots) {
        StringBuilder sb = new StringBuilder("[");
        for (Slot slot : slots) {
            sb.append(((ISlot)slot).clientsort$getIndexInContainer());
            sb.append(":[");
            sb.append(slot.getItem().getCount());
            sb.append(" ");
            sb.append(slot.getItem().getHoverName().getString());
            sb.append("], ");
        }
        return sb.length() == 1 ? "[]" : sb.substring(0, sb.length() - 2) + "]";
    }

    public static String listSlotIds(Iterable<Slot> slots) {
        StringBuilder sb = new StringBuilder("[");
        for (Slot slot : slots) {
            sb.append(((ISlot)slot).clientsort$getIndexInMenu());
            sb.append(":[");
            sb.append(slot.getItem().getCount());
            sb.append(" ");
            sb.append(slot.getItem().getHoverName().getString());
            sb.append("], ");
        }
        return sb.length() == 1 ? "[]" : sb.substring(0, sb.length() - 2) + "]";
    }

    public static String listSlotIndexArray(int[] indexes) {
        StringBuilder sb = new StringBuilder("[");
        for (int id : indexes) {
            sb.append(id);
            sb.append(", ");
        }
        return sb.length() == 1 ? "[]" : sb.substring(0, sb.length() - 2) + "]";
    }

    public static String listSlotMappingArray(int[] slotMapping) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < slotMapping.length - 1; i += 2) {
            sb.append(slotMapping[i]);
            sb.append("->");
            sb.append(slotMapping[i + 1]);
            sb.append(", ");
        }
        return sb.length() == 1 ? "[]" : sb.substring(0, sb.length() - 2) + "]";
    }

    private static String listContainerMenuSlots(AbstractContainerMenu menu) {
        StringBuilder sb = new StringBuilder("[");
        for (Slot slot : menu.slots) {
            sb.append(slot.index);
            sb.append(":[");
            sb.append(slot.getItem().getCount());
            sb.append(" ");
            sb.append(slot.getItem().getDisplayName().getString());
            sb.append("], ");
        }
        return sb.length() == 1 ? "[]" : sb.substring(0, sb.length() - 2) + "]";
    }
}
