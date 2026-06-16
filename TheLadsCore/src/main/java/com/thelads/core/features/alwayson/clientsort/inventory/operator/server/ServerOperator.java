/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.ChatFormatting
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.level.Level
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.inventory.operator.server;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.SingleUseOperator;
import com.thelads.core.features.alwayson.clientsort.network.handler.CollectResultHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.SortResultHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.StackFillResultHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.TransferResultHandler;
import com.thelads.core.features.alwayson.clientsort.order.SortContext;
import com.thelads.core.features.alwayson.clientsort.order.SortOrder;
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.StackFillPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.TransferPayload;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ServerOperator
extends SingleUseOperator {
    public ServerOperator(AbstractContainerScreen<?> screen, Slot originSlot, Operation op) {
        super(screen, originSlot, op);
    }

    @Override
    protected void sort(SortOrder sortOrder) {
        if (sortOrder.equals(SortOrder.NONE)) {
            return;
        }
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Cannot perform operation SORT: origin scope is empty!", new Object[0]);
            }
            return;
        }
        String id = UUID.randomUUID().toString();
        CollectResultHandler.onCompletion.put(id, collectResult -> {
            if (collectResult.isSuccess()) {
                SortResultHandler.onCompletion = sortResult -> {
                    if (!sortResult.isSuccess()) {
                        if (sortResult.isUnknown() || !Config.options().useClientFallback) {
                            this.setOverlayMessage((Component)Component.translatable((String)sortResult.translationKey));
                        } else {
                            SingleUseOperator.sort(this.screen, this.originSlot, true, sortOrder);
                        }
                    }
                };
                ServerOperator sorter = new ServerOperator(this.screen, this.originSlot, this.op);
                int[] slotMapping = sorter.createSlotMapping(sortOrder);
                if (ClientSortClient.debug()) {
                    com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Sending payload for operation SORT", new Object[0]);
                }
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new SortPayload(this.screen.getMenu().containerId, slotMapping));
            } else if (collectResult.isUnknown() || !Config.options().useClientFallback) {
                this.setOverlayMessage((Component)Component.translatable((String)collectResult.translationKey));
            } else {
                SingleUseOperator.sort(this.screen, this.originSlot, true, sortOrder);
            }
        });
        com.thelads.core.features.alwayson.clientsort.ClientSortClient.taskManager.schedule(20, () -> CollectResultHandler.onCompletion.remove(id));
        int[] scopeArray = this.createSlotIdArray(this.originScopeSlots);
        this.sendCollectPayload(scopeArray, id);
    }

    @Override
    protected void fillStacks() {
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Cannot perform operation STACK_FILL: origin scope is empty!", new Object[0]);
            }
            return;
        }
        if (this.otherScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Cannot perform operation STACK_FILL: other scope is empty!", new Object[0]);
            }
            return;
        }
        StackFillResultHandler.onCompletion = result -> {
            if (!result.isSuccess()) {
                if (result.isUnknown() || !Config.options().useClientFallback) {
                    this.setOverlayMessage((Component)Component.translatable((String)result.translationKey));
                } else {
                    SingleUseOperator.fillStacks(this.screen, this.originSlot, true);
                }
            }
        };
        int[] srcSlotIds = this.createSlotIdArray(this.originScopeSlots);
        int[] dstSlotIds = this.createSlotIdArray(this.otherScopeSlots);
        if (ClientSortClient.debug()) {
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Sending payload for operation STACK_FILL", new Object[0]);
        }
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new StackFillPayload(this.screen.getMenu().containerId, srcSlotIds, dstSlotIds, Config.options().transferReverseOrder));
    }

    @Override
    protected void matchTransfer() {
        this.transfer(ServerOperator.collectMatchingSlots(this.originScopeSlots, this.otherScopeStacks, Config.options().alwaysMatchByType, Config.options().typeMatchItemCache));
    }

    @Override
    protected void transfer() {
        this.transfer(null);
    }

    private void transfer(@Nullable Slot[] overrideSlots) {
        Slot[] originSlots;
        Slot[] slotArray = originSlots = overrideSlots != null ? overrideSlots : this.originScopeSlots;
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Cannot perform operation TRANSFER: origin scope is empty!", new Object[0]);
            }
            return;
        }
        if (originSlots.length == 0) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Cannot perform operation TRANSFER: origin slots is empty!", new Object[0]);
            }
            return;
        }
        if (this.otherScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Cannot perform operation TRANSFER: other scope is empty!", new Object[0]);
            }
            return;
        }
        TransferResultHandler.onCompletion = result -> {
            if (!result.isSuccess()) {
                if (result.isUnknown() || !Config.options().useClientFallback) {
                    this.setOverlayMessage((Component)Component.translatable((String)result.translationKey));
                } else if (overrideSlots != null) {
                    SingleUseOperator.transferMatching(this.screen, this.originSlot, true);
                } else {
                    SingleUseOperator.transfer(this.screen, this.originSlot, true);
                }
            }
        };
        int[] srcSlotIds = this.createSlotIdArray(originSlots);
        int[] dstSlotIds = this.createSlotIdArray(this.otherScopeSlots);
        if (ClientSortClient.debug()) {
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Sending payload for operation TRANSFER", new Object[0]);
        }
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new TransferPayload(this.screen.getMenu().containerId, srcSlotIds, dstSlotIds, Config.options().transferReverseOrder));
    }

    private int[] createSlotIdArray(Slot[] slots) {
        int[] slotIds = new int[slots.length];
        for (int i = 0; i < slots.length; ++i) {
            slotIds[i] = ((ISlot)slots[i]).clientsort$getIndexInMenu();
        }
        this.screenHelper.translateSlotIds(slotIds);
        return slotIds;
    }

    private void sendCollectPayload(int[] scopeArray, String id) {
        if (ClientSortClient.debug()) {
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Sending payload for operation COLLECT", new Object[0]);
        }
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new CollectPayload(this.screen.getMenu().containerId, scopeArray, id));
    }

    private int[] createSlotMapping(SortOrder sortOrder) {
        int[] sortedIds = new int[this.originScopeStacks.length];
        for (int i = 0; i < sortedIds.length; ++i) {
            sortedIds[i] = i;
        }
        sortedIds = sortOrder.sort(sortedIds, this.originScopeStacks, new SortContext((Level)Minecraft.getInstance().level));
        int[] slotMapping = new int[sortedIds.length * 2];
        for (int i = 0; i < sortedIds.length; ++i) {
            Slot from = this.originScopeSlots[sortedIds[i]];
            Slot to = this.originScopeSlots[i];
            slotMapping[i * 2] = ((ISlot)from).clientsort$getIndexInMenu();
            slotMapping[i * 2 + 1] = ((ISlot)to).clientsort$getIndexInMenu();
        }
        this.screenHelper.translateSlotIds(slotMapping);
        return slotMapping;
    }

    private void setOverlayMessage(Component message) {
        com.thelads.core.features.alwayson.clientsort.ClientSortClient.setOverlayMessage(this.screen, (Component)Localization.localized("name", new Object[0]).withStyle(ChatFormatting.RED).append("\n").append(message).append("\n").append((Component)Localization.localized("message", "checkLogs", new Object[0])), 100);
    }
}
