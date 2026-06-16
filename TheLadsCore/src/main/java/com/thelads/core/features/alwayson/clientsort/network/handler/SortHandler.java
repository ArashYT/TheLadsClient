/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.Container
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.ItemStack
 */
package com.thelads.core.features.alwayson.clientsort.network.handler;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.ServerClassPolicy;
import com.thelads.core.features.alwayson.clientsort.exception.PayloadHandlerException;
import com.thelads.core.features.alwayson.clientsort.network.handler.PayloadHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.validate.PolicyManager;
import com.thelads.core.features.alwayson.clientsort.network.handler.validate.SchemaValidator;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortResultPayload;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.util.TreeMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SortHandler
extends PayloadHandler {
    private SortHandler() {
    }

    public static void handle(SortPayload payload, MinecraftServer server, ServerPlayer player) {
        server.execute(() -> SortHandler.processPayload(server, player, payload.containerId(), menu -> SortHandler.checkPolicy(player, menu, payload.slotMapping()), menu -> SchemaValidator.validateSlotMapping(player, menu, payload.slotMapping()), menu -> SortHandler.sort(server, menu, payload.slotMapping()), SortPayload.TYPE, SortResultPayload.TYPE, (result, message) -> new SortResultPayload(result.code, (String)message)));
    }

    private static void sort(MinecraftServer server, AbstractContainerMenu menu, int[] slotMapping) throws PayloadHandlerException {
        TreeMap<Integer, ItemStack> stacks = new TreeMap<Integer, ItemStack>();
        for (Slot slot : menu.slots) {
            stacks.put(((ISlot)slot).clientsort$getIndexInMenu(), slot.getItem().copy());
        }
        for (int i = 0; i < slotMapping.length - 1; i += 2) {
            int srcSlotId = slotMapping[i];
            int dstSlotId = slotMapping[i + 1];
            Slot dstSlot = (Slot)menu.slots.get(dstSlotId);
            if (srcSlotId == dstSlotId) continue;
            dstSlot.setByPlayer((ItemStack)stacks.get(srcSlotId));
            try {
                int finalSrcSlotId = srcSlotId;
                int finalDstSlotId = dstSlotId;
                SortHandler.validate(server, (ItemStack)stacks.get(srcSlotId), dstSlot.getItem(), () -> String.format("Sort operation failed at slot mapping %d->%d", finalSrcSlotId, finalDstSlotId), msg -> SortHandler.setPolicy(menu, slotMapping, msg));
                continue;
            }
            catch (PayloadHandlerException.InconsistentStateException e) {
                for (int j = 0; j <= i; j += 2) {
                    srcSlotId = slotMapping[j];
                    ((Slot)menu.slots.get(srcSlotId)).set((ItemStack)stacks.get(srcSlotId));
                    dstSlotId = slotMapping[j + 1];
                    ((Slot)menu.slots.get(dstSlotId)).set((ItemStack)stacks.get(dstSlotId));
                }
                throw e;
            }
        }
    }

    private static void checkPolicy(ServerPlayer player, AbstractContainerMenu menu, int[] slotIds) throws PayloadHandlerException.UnsupportedOpException {
        Container container = slotIds.length > 0 ? ((Slot)menu.slots.get((int)slotIds[0])).container : null;
        Object object = ClientSort.getObj(container, menu);
        if (object == null) {
            throw new PayloadHandlerException.UnsupportedOpException("Reference object is null for inputs '%s', '%s'!".formatted(container == null ? "null" : container.getClass().getName(), menu == null ? "null" : menu.getClass().getName()));
        }
        if (container != player.getInventory()) {
            PolicyManager.checkPolicy(object.getClass(), bl -> bl.sortEnabled);
        }
    }

    private static void setPolicy(AbstractContainerMenu menu, int[] slotIds, String message) {
        Container container = slotIds.length > 0 ? ((Slot)menu.slots.get((int)slotIds[0])).container : null;
        Object object = ClientSort.getObj(container, menu);
        if (object == null) {
            ClientSort.LOG.warn("Could not set policy: reference object is null for inputs '{}', '{}'!", container == null ? "null" : container.getClass().getName(), menu == null ? "null" : menu.getClass().getName());
            return;
        }
        PolicyManager.setPolicy(new ServerClassPolicy(object.getClass().getName(), false, true, true), SortPayload.ID.toString(), message);
    }
}
