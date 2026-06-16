/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.ints.IntAVLTreeSet
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.Container
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 */
package com.thelads.core.features.alwayson.clientsort.network.handler.validate;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.exception.PayloadHandlerException;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SchemaValidator {
    private SchemaValidator() {
    }

    public static void validateSlotId(AbstractContainerMenu menu, int slotId) throws PayloadHandlerException.InvalidDataException {
        if (slotId < 0 || slotId >= menu.slots.size()) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Payload contains invalid slot ID %d out of range for menu with size %d!", slotId, menu.slots.size()));
        }
        int realId = ((ISlot)menu.slots.get(slotId)).clientsort$getIndexInMenu();
        if (slotId != realId) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Payload contains invalid slot ID %d which does not match the known ID of that slot (%d)!", slotId, realId));
        }
    }

    public static void validateContainerSlot(AbstractContainerMenu menu, int slotId, Container container) throws PayloadHandlerException.InvalidDataException {
        SchemaValidator.validateSlotId(menu, slotId);
        Slot slot = (Slot)menu.slots.get(slotId);
        if (container != slot.container) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Payload contains slots from different containers, first: '%s', now: '%s'!", container == null ? "null" : container.getClass().getName(), slot.container == null ? "null" : slot.container.getClass().getName()));
        }
    }

    public static void validateSlotArray(ServerPlayer player, AbstractContainerMenu menu, int[] slotIds) throws PayloadHandlerException.InvalidDataException {
        int minSlots = 1;
        if (slotIds.length < minSlots) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Slot array contains too few slots! Expected at least %d, got %d!", minSlots, slotIds.length));
        }
        SchemaValidator.validateSlotId(menu, slotIds[0]);
        Container container = ((Slot)menu.slots.get((int)slotIds[0])).container;
        IntAVLTreeSet checkedSlots = new IntAVLTreeSet();
        ItemStack testItem = Items.LIGHT.getDefaultInstance();
        for (int slotId : slotIds) {
            SchemaValidator.validateContainerSlot(menu, slotId, container);
            if (!checkedSlots.add(slotId)) {
                throw new PayloadHandlerException.InvalidDataException(String.format("Slot array contains duplicate slot %d!", slotId));
            }
            Slot slot = (Slot)menu.slots.get(slotId);
            boolean accessible = true;
            if (slot.hasItem()) {
                if (!slot.mayPickup((Player)player)) {
                    accessible = false;
                }
            } else if (!slot.container.canPlaceItem(slotId, testItem) || !slot.mayPlace(testItem)) {
                accessible = false;
            }
            if (accessible) continue;
            throw new PayloadHandlerException.InvalidDataException(String.format("Slot array contains inaccessible slot %d with item '%s'!", slotId, slot.getItem()));
        }
    }

    public static void validateSlotMapping(ServerPlayer player, AbstractContainerMenu menu, int[] slotMapping) throws PayloadHandlerException.InvalidDataException {
        int i;
        int minSlots = 2;
        if (slotMapping.length < minSlots) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Slot mapping contains too few slots! Expected at least %d, got %d!", minSlots, slotMapping.length));
        }
        if (slotMapping.length % 2 != 0) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Slot mapping contains an uneven number of slots (%d)!", slotMapping.length));
        }
        SchemaValidator.validateSlotId(menu, slotMapping[0]);
        Container container = ((Slot)menu.slots.get((int)slotMapping[0])).container;
        IntAVLTreeSet checkedSlots = new IntAVLTreeSet();
        for (i = 0; i < slotMapping.length; i += 2) {
            Slot srcSlot;
            int srcId = slotMapping[i];
            int dstId = slotMapping[i + 1];
            SchemaValidator.validateContainerSlot(menu, srcId, container);
            if (!checkedSlots.add(srcId)) {
                throw new PayloadHandlerException.InvalidDataException(String.format("Slot mapping contains duplicate source slot %d!", srcId));
            }
            SchemaValidator.validateContainerSlot(menu, dstId, container);
            if (srcId == dstId || !(srcSlot = (Slot)menu.slots.get(srcId)).hasItem() || srcSlot.mayPickup((Player)player)) continue;
            throw new PayloadHandlerException.InvalidDataException(String.format("Slot mapping contains inaccessible slot %d with item '%s'!", srcId, srcSlot.getItem()));
        }
        for (i = 1; i < slotMapping.length; i += 2) {
            int dstId = slotMapping[i];
            if (checkedSlots.remove(dstId)) continue;
            throw new PayloadHandlerException.InvalidDataException(String.format("Slot mapping contains duplicate destination slot or destination slot that does not appear as source slot (%d)!", dstId));
        }
        if (!checkedSlots.isEmpty()) {
            throw new PayloadHandlerException.InvalidDataException(String.format("Slot mapping contains %d source slots that do not appear as destination slots.", checkedSlots.size()));
        }
    }
}
