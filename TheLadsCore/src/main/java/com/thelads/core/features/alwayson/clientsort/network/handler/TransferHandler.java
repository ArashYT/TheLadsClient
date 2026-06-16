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
import com.thelads.core.features.alwayson.clientsort.network.payload.TransferPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.TransferResultPayload;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TransferHandler
extends PayloadHandler {
    private TransferHandler() {
    }

    public static void handle(TransferPayload payload, MinecraftServer server, ServerPlayer player) {
        server.execute(() -> TransferHandler.processPayload(server, player, payload.srcContainerId(), menu -> TransferHandler.checkPolicy(menu, payload.srcSlotIds(), payload.dstSlotIds()), menu -> {
            SchemaValidator.validateSlotArray(player, menu, payload.srcSlotIds());
            SchemaValidator.validateSlotArray(player, menu, payload.dstSlotIds());
        }, menu -> TransferHandler.transfer(server, menu, payload.srcSlotIds(), payload.dstSlotIds(), payload.reversed()), TransferPayload.TYPE, TransferResultPayload.TYPE, (result, message) -> new TransferResultPayload(result.code, (String)message)));
    }

    private static void transfer(MinecraftServer server, AbstractContainerMenu menu, int[] srcSlotIds, int[] dstSlotIds, boolean reversed) throws PayloadHandlerException {
        int start = reversed ? srcSlotIds.length - 1 : 0;
        Function<Integer, Boolean> end = reversed ? i -> i >= 0 : i -> i < srcSlotIds.length;
        Function<Integer, Integer> step = reversed ? i -> i - 1 : i -> i + 1;
        int i2 = start;
        while (end.apply(i2).booleanValue()) {
            int srcSlotId = srcSlotIds[i2];
            Slot srcSlot = (Slot)menu.slots.get(srcSlotId);
            ItemStack srcStack = srcSlot.getItem();
            ItemStack srcStackCopy = srcStack.copy();
            if (!srcStack.isEmpty()) {
                ItemStack expected;
                ItemStack dstStackCopy;
                ItemStack dstStack;
                Slot dstSlot;
                for (int dstSlotId : dstSlotIds) {
                    dstSlot = (Slot)menu.slots.get(dstSlotId);
                    dstStack = dstSlot.getItem();
                    dstStackCopy = dstStack.copy();
                    if (dstStack.isEmpty() || dstStack.getCount() >= dstSlot.getMaxStackSize(dstStack) || !ItemStack.isSameItemSameComponents((ItemStack)srcStack, (ItemStack)dstStack)) continue;
                    expected = srcStack.copyWithCount(Math.min(srcStack.getCount() + dstStack.getCount(), dstSlot.getMaxStackSize(dstStack)));
                    dstSlot.safeInsert(srcStack);
                    final int capDstSlotId1 = dstSlotId; final ItemStack capDstStackCopy1 = dstStackCopy;
                    TransferHandler.validate(server, expected, dstSlot.getItem(), () -> String.format("Transfer operation failed to safe-insert from slot %d with item '%s' to slot %d with item '%s'", srcSlotId, srcStackCopy, capDstSlotId1, capDstStackCopy1), msg -> TransferHandler.setPolicy(menu, dstSlotIds, msg));
                    if (srcStack.isEmpty()) break;
                }
                if (!srcStack.isEmpty()) {
                    for (int dstSlotId : dstSlotIds) {
                        dstSlot = (Slot)menu.slots.get(dstSlotId);
                        dstStack = dstSlot.getItem();
                        dstStackCopy = dstStack.copy();
                        if (!dstStack.isEmpty()) continue;
                        expected = srcStack.copyWithCount(Math.min(srcStack.getCount(), dstSlot.getMaxStackSize(srcStack)));
                        dstSlot.safeInsert(srcStack);
                        final int capDstSlotId2 = dstSlotId; final ItemStack capDstStackCopy2 = dstStackCopy;
                        TransferHandler.validate(server, expected, dstSlot.getItem(), () -> String.format("Transfer operation failed to safe-insert from slot %d with item '%s' to slot %d with item '%s'", srcSlotId, srcStackCopy, capDstSlotId2, capDstStackCopy2), msg -> TransferHandler.setPolicy(menu, dstSlotIds, msg));
                        break;
                    }
                }
            }
            i2 = step.apply(i2);
        }
    }

    private static void checkPolicy(AbstractContainerMenu menu, int[] srcSlotIds, int[] dstSlotIds) throws PayloadHandlerException.UnsupportedOpException {
        Container srcContainer = srcSlotIds.length > 0 ? ((Slot)menu.slots.get((int)srcSlotIds[0])).container : null;
        Object srcObject = ClientSort.getObj(srcContainer, menu);
        if (srcObject == null) {
            throw new PayloadHandlerException.UnsupportedOpException("Reference src object is null for inputs '%s', '%s'!".formatted(srcContainer == null ? "null" : srcContainer.getClass().getName(), menu == null ? "null" : menu.getClass().getName()));
        }
        Container dstContainer = dstSlotIds.length > 0 ? ((Slot)menu.slots.get((int)dstSlotIds[0])).container : null;
        Object dstObject = ClientSort.getObj(dstContainer, menu);
        if (dstObject == null) {
            throw new PayloadHandlerException.UnsupportedOpException("Reference dst object is null for inputs '%s', '%s'!".formatted(dstContainer == null ? "null" : dstContainer.getClass().getName(), menu == null ? "null" : menu.getClass().getName()));
        }
        PolicyManager.checkPolicy(srcObject.getClass(), bl -> bl.transferEnabled);
        PolicyManager.checkPolicy(dstObject.getClass(), bl -> bl.transferEnabled);
    }

    private static void setPolicy(AbstractContainerMenu menu, int[] dstSlotIds, String message) {
        Container dstContainer = dstSlotIds.length > 0 ? ((Slot)menu.slots.get((int)dstSlotIds[0])).container : null;
        Object object = ClientSort.getObj(dstContainer, menu);
        if (object == null) {
            ClientSort.LOG.warn("Could not set policy: reference object is null for inputs '{}', '{}'!", dstContainer == null ? "null" : dstContainer.getClass().getName(), menu == null ? "null" : menu.getClass().getName());
            return;
        }
        PolicyManager.setPolicy(new ServerClassPolicy(object.getClass().getName(), true, true, false), TransferPayload.ID.toString(), message);
    }
}
