/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.ItemStack
 */
package com.thelads.core.features.alwayson.clientsort.inventory.operator.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.interaction.InteractionManager;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.client.ClientOperator;
import com.thelads.core.features.alwayson.clientsort.util.SoundManager;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ClientCreativeOperator
extends ClientOperator {
    public ClientCreativeOperator(AbstractContainerScreen<?> screen, Slot originSlot, Operation op) {
        super(screen, originSlot, op);
    }

    @Override
    protected void collect() {
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation COLLECT: origin scope is empty!", new Object[0]);
            }
            return;
        }
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.info("Starting operation COLLECT", new Object[0]);
        }
        block0: for (int i = this.originScopeSlots.length - 1; i >= 0; --i) {
            Slot srcSlot = this.originScopeSlots[i];
            ItemStack srcStack = this.originScopeStacks[i];
            if (srcStack.isEmpty() || srcStack.getCount() >= srcSlot.getMaxStackSize(srcStack)) continue;
            for (int j = 0; j < i; ++j) {
                Slot dstSlot = this.originScopeSlots[j];
                ItemStack dstStack = this.originScopeStacks[j];
                if (dstStack.isEmpty() || dstStack.getCount() >= dstSlot.getMaxStackSize(dstStack) || !ItemStack.isSameItemSameComponents((ItemStack)srcStack, (ItemStack)dstStack)) continue;
                int delta = dstSlot.getMaxStackSize(dstStack) - dstStack.getCount();
                delta = Math.min(delta, srcStack.getCount());
                srcStack.shrink(delta);
                dstStack.grow(delta);
                int srcSlotId = ((ISlot)srcSlot).clientsort$getIndexInMenu();
                int dstSlotId = ((ISlot)dstSlot).clientsort$getIndexInMenu();
                InteractionManager.push(() -> {
                    Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(srcStack.copy(), srcSlotId);
                    Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(dstStack.copy(), dstSlotId);
                    return InteractionManager.TICK_WAITER;
                });
                if (srcStack.getCount() <= 0) continue block0;
            }
        }
        InteractionManager.push(() -> {
            Minecraft.getInstance().player.inventoryMenu.broadcastChanges();
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.info("Finished operation COLLECT", new Object[0]);
            }
            return InteractionManager.TICK_WAITER;
        });
    }

    @Override
    protected void sort(int[] key, boolean playSound) {
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation SORT: origin scope is empty!", new Object[0]);
            }
            return;
        }
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.info("Starting operation SORT", new Object[0]);
        }
        for (int i = 0; i < key.length; ++i) {
            ItemStack srcItem = this.originScopeStacks[key[i]];
            ItemStack dstItem = this.originScopeStacks[i];
            if (srcItem.isEmpty() && dstItem.isEmpty()) continue;
            int dstSlotId = ((ISlot)this.originScopeSlots[i]).clientsort$getIndexInMenu();
            InteractionManager.push(() -> {
                Minecraft.getInstance().player.inventoryMenu.getSlot(dstSlotId).set(srcItem);
                Minecraft.getInstance().gameMode.handleCreativeModeItemAdd(srcItem, dstSlotId);
                if (playSound) {
                    SoundManager.play();
                }
                return InteractionManager.TICK_WAITER;
            });
        }
        InteractionManager.push(() -> {
            Minecraft.getInstance().player.inventoryMenu.broadcastChanges();
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.info("Finished operation SORT", new Object[0]);
            }
            return InteractionManager.TICK_WAITER;
        });
    }

    @Override
    protected void fillStacks() {
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.warn("Operation STACK_FILL is not supported by {}", this.getClass().getSimpleName());
        }
    }

    @Override
    protected void matchTransfer() {
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.warn("Operation MATCH_TRANSFER is not supported by {}", this.getClass().getSimpleName());
        }
    }

    @Override
    protected void transfer() {
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.warn("Operation TRANSFER is not supported by {}", this.getClass().getSimpleName());
        }
    }
}
