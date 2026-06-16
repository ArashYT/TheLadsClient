/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.tags.ItemTags
 *  net.minecraft.world.inventory.ContainerInput
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.BundleItem
 *  net.minecraft.world.item.ItemStack
 */
package com.thelads.core.features.alwayson.clientsort.inventory.operator.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.interaction.InteractionManager;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.client.ClientOperator;
import com.thelads.core.features.alwayson.clientsort.util.SoundManager;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractContainerScreenAccessor;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.function.Function;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

public class ClientSurvivalOperator
extends ClientOperator {
    public ClientSurvivalOperator(AbstractContainerScreen<?> screen, Slot originSlot, Operation op) {
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
        ArrayDeque<InteractionManager.InteractionEvent> clickEvents = new ArrayDeque<InteractionManager.InteractionEvent>();
        for (int i = this.originScopeSlots.length - 1; i >= 0; --i) {
            Slot srcSlot = this.originScopeSlots[i];
            ItemStack srcStack = this.originScopeStacks[i];
            if (srcStack.isEmpty() || srcStack.getCount() >= srcSlot.getMaxStackSize(srcStack)) continue;
            clickEvents.add(this.createClickEvent(srcSlot, 0, false));
            for (int j = 0; j < i; ++j) {
                Slot dstSlot = this.originScopeSlots[j];
                ItemStack dstStack = this.originScopeStacks[j];
                if (dstStack.isEmpty() || dstStack.getCount() >= dstSlot.getMaxStackSize(dstStack) || !ItemStack.isSameItemSameComponents((ItemStack)srcStack, (ItemStack)dstStack)) continue;
                int delta = dstSlot.getMaxStackSize(dstStack) - dstStack.getCount();
                delta = Math.min(delta, srcStack.getCount());
                srcStack.shrink(delta);
                dstStack.grow(delta);
                clickEvents.add(this.createClickEvent(dstSlot, 0, false));
                if (srcStack.getCount() <= 0) break;
            }
            if (clickEvents.size() > 1) {
                InteractionManager.pushAll(clickEvents);
                InteractionManager.triggerSend(InteractionManager.TriggerType.GUI_CONFIRM);
                clickEvents.clear();
                if (srcStack.getCount() > 0) {
                    InteractionManager.push(this.createClickEvent(srcSlot, 0, false));
                } else {
                    this.originScopeStacks[i] = ItemStack.EMPTY;
                }
            }
            clickEvents.clear();
        }
        if (ClientSortClient.debug()) {
            InteractionManager.push(() -> {
                ClientSortClient.LOG.info("Finished operation COLLECT", new Object[0]);
                return InteractionManager.TICK_WAITER;
            });
        }
    }

    @Override
    protected void sort(int[] sortedIds, boolean playSound) {
        int i;
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation SORT: origin scope is empty!", new Object[0]);
            }
            return;
        }
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.info("Starting operation SORT", new Object[0]);
        }
        int slotCount = this.originScopeStacks.length;
        int[] srcToDst = new int[slotCount];
        for (int i2 = 0; i2 < slotCount; ++i2) {
            srcToDst[sortedIds[i2]] = i2;
        }
        BitSet done = new BitSet(slotCount);
        BitSet empty = new BitSet(slotCount);
        for (i = 0; i < slotCount; ++i) {
            if (i == sortedIds[i]) {
                done.set(i);
            }
            if (!this.originScopeStacks[i].isEmpty()) continue;
            empty.set(i);
        }
        block2: for (i = 0; i < slotCount; ++i) {
            if (done.get(i)) continue;
            if (empty.get(sortedIds[i])) {
                done.set(sortedIds[i]);
                continue;
            }
            InteractionManager.push(this.createClickEvent(this.originScopeSlots[sortedIds[i]], 0, playSound));
            ItemStack carriedStack = this.originScopeStacks[sortedIds[i]];
            this.originScopeStacks[sortedIds[i]] = ItemStack.EMPTY;
            empty.set(sortedIds[i]);
            Slot workingSlot = this.originScopeSlots[sortedIds[i]];
            int dstId = i;
            do {
                boolean clickOnItemWithBundle;
                if (!empty.get(dstId) && ItemStack.isSameItemSameComponents((ItemStack)carriedStack, (ItemStack)this.originScopeStacks[dstId])) {
                    if (carriedStack.getCount() == this.originScopeStacks[dstId].getCount()) {
                        done.set(dstId);
                        dstId = srcToDst[dstId];
                        continue;
                    }
                    if (carriedStack.getCount() < this.originScopeStacks[dstId].getCount()) {
                        Slot dstSlot = this.originScopeSlots[dstId];
                        InteractionManager.push(this.createClickEvent(workingSlot, 0, playSound));
                        InteractionManager.push(this.createClickEvent(dstSlot, 0, playSound));
                        InteractionManager.push(this.createClickEvent(workingSlot, 0, playSound));
                        InteractionManager.push(this.createClickEvent(dstSlot, 0, playSound));
                        InteractionManager.push(this.createClickEvent(workingSlot, 0, playSound));
                        ItemStack tmp = carriedStack;
                        carriedStack = this.originScopeStacks[dstId];
                        this.originScopeStacks[dstId] = tmp;
                        done.set(dstId);
                        dstId = srcToDst[dstId];
                        continue;
                    }
                }
                int mouseButton = 0;
                boolean clickOnBundleWithItem = ClientSurvivalOperator.isBundle(this.originScopeStacks[dstId]) && !carriedStack.isEmpty();
                boolean bl = clickOnItemWithBundle = ClientSurvivalOperator.isBundle(carriedStack) && !this.originScopeStacks[dstId].isEmpty();
                if (!Config.options().bundlesUseRightClick && (clickOnBundleWithItem || clickOnItemWithBundle)) {
                    mouseButton = 1;
                }
                InteractionManager.push(this.createClickEvent(this.originScopeSlots[dstId], mouseButton, playSound));
                ItemStack tmp = carriedStack;
                carriedStack = this.originScopeStacks[dstId];
                this.originScopeStacks[dstId] = tmp;
                done.set(dstId);
                if (empty.get(dstId)) continue block2;
                dstId = srcToDst[dstId];
            } while (!done.get(dstId));
        }
        if (ClientSortClient.debug()) {
            InteractionManager.push(() -> {
                ClientSortClient.LOG.info("Finished operation SORT", new Object[0]);
                return InteractionManager.TICK_WAITER;
            });
        }
    }

    @Override
    protected void fillStacks() {
        boolean playSound;
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation STACK_FILL: origin scope is empty!", new Object[0]);
            }
            return;
        }
        if (this.otherScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation STACK_FILL: other scope is empty!", new Object[0]);
            }
            return;
        }
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.info("Starting operation STACK_FILL", new Object[0]);
        }
        if (playSound = SoundManager.shouldPlayOtherSounds()) {
            SoundManager.resetForCount(SoundManager.estimateStackFillSounds(this.originScopeStacks, this.otherScopeStacks));
        }
        ArrayDeque<InteractionManager.InteractionEvent> clickEvents = new ArrayDeque<InteractionManager.InteractionEvent>();
        for (int i = this.originScopeSlots.length - 1; i >= 0; --i) {
            Slot srcSlot = this.originScopeSlots[i];
            ItemStack srcStack = this.originScopeStacks[i];
            if (srcStack.isEmpty()) continue;
            clickEvents.add(this.createClickEvent(srcSlot, 0, false));
            for (int j = 0; j < this.otherScopeSlots.length; ++j) {
                Slot dstSlot = this.otherScopeSlots[j];
                ItemStack dstStack = this.otherScopeStacks[j];
                if (dstStack.isEmpty() || dstStack.getCount() >= dstSlot.getMaxStackSize(dstStack) || !ItemStack.isSameItemSameComponents((ItemStack)srcStack, (ItemStack)dstStack)) continue;
                int delta = dstSlot.getMaxStackSize(dstStack) - dstStack.getCount();
                delta = Math.min(delta, srcStack.getCount());
                srcStack.setCount(srcStack.getCount() - delta);
                dstStack.setCount(dstStack.getCount() + delta);
                clickEvents.add(this.createClickEvent(dstSlot, 0, playSound));
                if (srcStack.getCount() <= 0) break;
            }
            if (clickEvents.size() > 1) {
                InteractionManager.pushAll(clickEvents);
                InteractionManager.triggerSend(InteractionManager.TriggerType.GUI_CONFIRM);
                clickEvents.clear();
                if (srcStack.getCount() > 0) {
                    InteractionManager.push(this.createClickEvent(srcSlot, 0, false));
                } else {
                    this.originScopeStacks[i] = ItemStack.EMPTY;
                }
            }
            clickEvents.clear();
        }
        if (ClientSortClient.debug()) {
            InteractionManager.push(() -> {
                ClientSortClient.LOG.info("Finished operation STACK_FILL", new Object[0]);
                return InteractionManager.TICK_WAITER;
            });
        }
    }

    @Override
    protected void matchTransfer() {
        this.transfer(ClientSurvivalOperator.collectMatchingSlots(this.originScopeSlots, this.otherScopeStacks, Config.options().alwaysMatchByType, Config.options().typeMatchItemCache));
    }

    @Override
    protected void transfer() {
        this.transfer(this.originScopeSlots);
    }

    protected void transfer(Slot[] originSlots) {
        boolean playSound;
        if (this.originScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation (MATCH_)TRANSFER: origin scope is empty!", new Object[0]);
            }
            return;
        }
        if (originSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation (MATCH_)TRANSFER: origin slots is empty!", new Object[0]);
            }
            return;
        }
        if (this.otherScopeSlots.length == 0) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.warn("Cannot perform operation (MATCH_)TRANSFER: other scope is empty!", new Object[0]);
            }
            return;
        }
        if (ClientSortClient.debug()) {
            ClientSortClient.LOG.info("Starting operation (MATCH_)TRANSFER", new Object[0]);
        }
        ItemStack[] originStacks = this.originScopeStacks;
        if (originSlots != this.originScopeSlots) {
            originStacks = new ItemStack[originSlots.length];
            for (int i2 = 0; i2 < originSlots.length; ++i2) {
                originStacks[i2] = originSlots[i2].getItem().copy();
            }
        }
        if (playSound = SoundManager.shouldPlayOtherSounds()) {
            SoundManager.resetForCount(SoundManager.estimateTransferSounds(originStacks, this.otherScopeStacks));
        }
        ArrayDeque<InteractionManager.InteractionEvent> clickEvents = new ArrayDeque<InteractionManager.InteractionEvent>();
        boolean reversed = Config.options().transferReverseOrder;
        int start = reversed ? originSlots.length - 1 : 0;
        Function<Integer, Boolean> end = reversed ? i -> i >= 0 : i -> i < originSlots.length;
        Function<Integer, Integer> step = reversed ? i -> i - 1 : i -> i + 1;
        int i3 = start;
        while (end.apply(i3).booleanValue()) {
            Slot srcSlot = originSlots[i3];
            ItemStack srcStack = originStacks[i3];
            if (!srcStack.isEmpty()) {
                clickEvents.add(this.createClickEvent(srcSlot, 0, false));
                int emptySlotId = -1;
                for (int j = 0; j < this.otherScopeSlots.length; ++j) {
                    Slot dstSlot = this.otherScopeSlots[j];
                    ItemStack dstStack = this.otherScopeStacks[j];
                    if (dstStack.isEmpty()) {
                        if (emptySlotId != -1) continue;
                        emptySlotId = j;
                        continue;
                    }
                    if (dstStack.getCount() >= dstSlot.getMaxStackSize(dstStack) || !ItemStack.isSameItemSameComponents((ItemStack)srcStack, (ItemStack)dstStack)) continue;
                    int delta = dstSlot.getMaxStackSize(dstStack) - dstStack.getCount();
                    delta = Math.min(delta, srcStack.getCount());
                    srcStack.setCount(srcStack.getCount() - delta);
                    dstStack.setCount(dstStack.getCount() + delta);
                    clickEvents.add(this.createClickEvent(dstSlot, 0, playSound));
                    if (srcStack.isEmpty()) break;
                }
                if (!srcStack.isEmpty() && emptySlotId != -1) {
                    Slot dstSlot = this.otherScopeSlots[emptySlotId];
                    this.otherScopeStacks[emptySlotId] = srcStack.copy();
                    srcStack.setCount(0);
                    clickEvents.add(this.createClickEvent(dstSlot, 0, playSound));
                }
                if (clickEvents.size() > 1) {
                    InteractionManager.pushAll(clickEvents);
                    InteractionManager.triggerSend(InteractionManager.TriggerType.GUI_CONFIRM);
                    clickEvents.clear();
                    if (srcStack.getCount() > 0) {
                        InteractionManager.push(this.createClickEvent(srcSlot, 0, false));
                    } else {
                        originStacks[i3] = ItemStack.EMPTY;
                    }
                }
                clickEvents.clear();
            }
            i3 = step.apply(i3);
        }
        if (ClientSortClient.debug()) {
            InteractionManager.push(() -> {
                ClientSortClient.LOG.info("Finished operation (MATCH_)TRANSFER", new Object[0]);
                return InteractionManager.TICK_WAITER;
            });
        }
    }

    private InteractionManager.InteractionEvent createClickEvent(Slot slot, int button, boolean playSound) {
        return new InteractionManager.CallbackEvent(() -> {
            ((AbstractContainerScreenAccessor)this.screen).clientsort$slotClicked(slot, ((ISlot)slot).clientsort$getIndexInMenu(), button, ContainerInput.PICKUP);
            if (playSound) {
                SoundManager.play();
            }
            return InteractionManager.TICK_WAITER;
        });
    }

    private static boolean isBundle(ItemStack stack) {
        return stack.is(ItemTags.BUNDLES) || stack.getItem() instanceof BundleItem;
    }
}
