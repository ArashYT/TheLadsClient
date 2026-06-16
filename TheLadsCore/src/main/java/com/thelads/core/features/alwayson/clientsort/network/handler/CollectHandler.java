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
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectResultPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CollectHandler
extends PayloadHandler {
    private CollectHandler() {
    }

    public static void handle(CollectPayload payload, MinecraftServer server, ServerPlayer player) {
        server.execute(() -> CollectHandler.processPayload(server, player, payload.containerId(), menu -> CollectHandler.checkPolicy(player, menu, payload.slotIds()), menu -> SchemaValidator.validateSlotArray(player, menu, payload.slotIds()), menu -> CollectHandler.collect(server, menu, payload.slotIds()), CollectPayload.TYPE, CollectResultPayload.TYPE, (result, message) -> new CollectResultPayload(result.code, (String)message, payload.id())));
    }

    private static void collect(MinecraftServer server, AbstractContainerMenu menu, int[] slotIds) throws PayloadHandlerException {
        block0: for (int i = slotIds.length - 1; i >= 0; --i) {
            int srcSlotId = slotIds[i];
            Slot srcSlot = (Slot)menu.slots.get(srcSlotId);
            ItemStack srcStack = srcSlot.getItem();
            ItemStack srcStackCopy = srcStack.copy();
            if (srcStack.isEmpty() || srcStack.getCount() >= srcStack.getItem().getDefaultMaxStackSize()) continue;
            for (int j = 0; j < i; ++j) {
                int dstSlotId = slotIds[j];
                Slot dstSlot = (Slot)menu.slots.get(dstSlotId);
                ItemStack dstStack = dstSlot.getItem();
                ItemStack dstStackCopy = dstStack.copy();
                if (dstStack.isEmpty() || dstStack.getCount() >= dstStack.getItem().getDefaultMaxStackSize() || !ItemStack.isSameItemSameComponents((ItemStack)srcStack, (ItemStack)dstStack)) continue;
                dstSlot.safeInsert(srcStack);
                ItemStack expected = srcStackCopy.copyWithCount(Math.min(srcStackCopy.getCount() + dstStackCopy.getCount(), dstSlot.getMaxStackSize(srcStackCopy)));
                CollectHandler.validate(server, expected, dstSlot.getItem(), () -> String.format("Collect operation failed to safe-insert from slot %d with item '%s' to slot %d with item '%s'", srcSlotId, srcStackCopy, dstSlotId, dstStackCopy), msg -> CollectHandler.setPolicy(menu, slotIds, msg));
                if (srcStack.isEmpty()) continue block0;
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
        PolicyManager.setPolicy(new ServerClassPolicy(object.getClass().getName(), false, true, true), CollectPayload.ID.toString(), message);
    }
}
