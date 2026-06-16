/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.inventory.operator;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.compat.itemlocks.ItemLocksCompat;
import com.thelads.core.features.alwayson.clientsort.compat.stacktnc.StackTncCompat;
import com.thelads.core.features.alwayson.clientsort.config.ClassPolicy;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.gui.TriggerButtonManager;
import com.thelads.core.features.alwayson.clientsort.interaction.InteractionManager;
import com.thelads.core.features.alwayson.clientsort.inventory.Scope;
import com.thelads.core.features.alwayson.clientsort.inventory.helper.ContainerScreenHelper;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.client.ClientCreativeOperator;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.client.ClientSurvivalOperator;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.server.ServerOperator;
import com.thelads.core.features.alwayson.clientsort.order.SortOrder;
import com.thelads.core.features.alwayson.clientsort.util.PolicyManager;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.AbstractContainerScreenAccessor;
import com.thelads.core.features.alwayson.clientsort.util.SlotLogUtil;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

public abstract class SingleUseOperator {
    protected final AbstractContainerScreen<?> screen;
    protected final ContainerScreenHelper<? extends AbstractContainerScreen<?>> screenHelper;
    protected final Operation op;
    protected final Slot originSlot;
    protected final Slot[] originScopeSlots;
    protected final ItemStack[] originScopeStacks;
    protected final Slot[] otherScopeSlots;
    protected final ItemStack[] otherScopeStacks;

    protected SingleUseOperator(AbstractContainerScreen<?> screen, Slot originSlot, Operation op) {
        Slot otherSlot;
        this.screen = screen;
        this.screenHelper = ContainerScreenHelper.of(screen);
        this.originSlot = originSlot;
        this.op = op;
        Scope originScope = this.screenHelper.getScope(originSlot);
        this.originScopeSlots = this.collectSlots(originSlot, originScope);
        if (ClientSortClient.debug()) {
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Discovered {} slots in origin scope ({} - {}):", this.originScopeSlots.length, originScope.ordinal(), originScope.name());
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info(SlotLogUtil.listSlotIds(List.of(this.originScopeSlots)), new Object[0]);
        }
        this.originScopeStacks = new ItemStack[this.originScopeSlots.length];
        for (int i = 0; i < this.originScopeSlots.length; ++i) {
            this.originScopeStacks[i] = this.originScopeSlots[i].getItem().copy();
        }
        LocalPlayer player = Objects.requireNonNull(Minecraft.getInstance().player);
        Slot slot = otherSlot = originSlot.container == player.getInventory() ? TriggerButtonManager.getContainerRefSlot(op) : TriggerButtonManager.getPlayerRefSlot(op);
        if (otherSlot == null || !op.isDirectional()) {
            this.otherScopeSlots = new Slot[0];
            this.otherScopeStacks = new ItemStack[0];
            return;
        }
        Scope otherScope = this.screenHelper.getScope(otherSlot);
        this.otherScopeSlots = this.collectSlots(otherSlot, otherScope);
        if (ClientSortClient.debug()) {
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Discovered {} slots in other scope ({} - {}):", this.otherScopeSlots.length, otherScope.ordinal(), otherScope.name());
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info(SlotLogUtil.listSlotIds(List.of(this.otherScopeSlots)), new Object[0]);
        }
        this.otherScopeStacks = new ItemStack[this.otherScopeSlots.length];
        for (int i = 0; i < this.otherScopeSlots.length; ++i) {
            this.otherScopeStacks[i] = this.otherScopeSlots[i].getItem().copy();
        }
    }

    private Slot[] collectSlots(Slot refSlot, Scope scope) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (scope == Scope.INVALID) {
            return new Slot[0];
        }
        ItemStack testItem = Items.LIGHT.getDefaultInstance();
        ArrayList<Slot> collectedSlots = new ArrayList<Slot>();
        for (Slot slot : this.screenHelper.getGroupForSlot(refSlot, scope)) {
            Object object;
            int slotId = ((ISlot)slot).clientsort$getIndexInMenu();
            int slotIdx = ((ISlot)slot).clientsort$getIndexInContainer();
            if (!slot.hasItem() ? !slot.container.canPlaceItem(slotId, testItem) || !slot.mayPlace(testItem) : player != null && !slot.mayPickup((Player)player)) continue;
            if (ItemLocksCompat.isLocked(slot) || StackTncCompat.isLocked(slot) || (object = ClientSort.getObj(slot, this.screen.getMenu())) == null) continue;
            boolean isPlayerInv = slot.container instanceof Inventory;
            Component invTitle = isPlayerInv ? ((AbstractContainerScreenAccessor)this.screen).clientsort$getPlayerInventoryTitle() : this.screen.getTitle();
            @Nullable ClassPolicy policy = PolicyManager.getPolicy(object.getClass(), invTitle.getString());
            if (policy != null && policy.ignoredSlots().contains(slotIdx)) continue;
            collectedSlots.add(slot);
        }
        return collectedSlots.toArray(new Slot[0]);
    }

    protected static void startClientOp() {
        com.thelads.core.features.alwayson.clientsort.ClientSortClient.operatingClient = true;
    }

    protected static void endClientOp() {
        InteractionManager.push(() -> {
            Runnable nextOp = (Runnable)com.thelads.core.features.alwayson.clientsort.ClientSortClient.clientOpQueue.poll();
            if (nextOp != null) {
                Minecraft.getInstance().execute(nextOp);
            } else {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.operatingClient = false;
            }
            return InteractionManager.TICK_WAITER;
        });
    }

    public static boolean sort(AbstractContainerScreen<?> screen, Slot originSlot, boolean onlyClient, SortOrder sortOrder) {
        if (sortOrder.equals(SortOrder.NONE)) {
            return false;
        }
        return SingleUseOperator.operate(screen, originSlot, onlyClient, Operation.SORT, op -> op.sort(sortOrder));
    }

    public static boolean fillStacks(AbstractContainerScreen<?> screen, Slot originSlot, boolean onlyClient) {
        return SingleUseOperator.operate(screen, originSlot, onlyClient, Operation.STACK_FILL, SingleUseOperator::fillStacks);
    }

    public static boolean transferMatching(AbstractContainerScreen<?> screen, Slot originSlot, boolean onlyClient) {
        return SingleUseOperator.operate(screen, originSlot, onlyClient, Operation.MATCH_TRANSFER, SingleUseOperator::matchTransfer);
    }

    public static boolean transfer(AbstractContainerScreen<?> screen, Slot originSlot, boolean onlyClient) {
        return SingleUseOperator.operate(screen, originSlot, onlyClient, Operation.TRANSFER, SingleUseOperator::transfer);
    }

    private static boolean operate(AbstractContainerScreen<?> screen, Slot originSlot, boolean onlyClient, Operation operation, Consumer<SingleUseOperator> wrapper) {
        block15: {
            if (Minecraft.getInstance().player.isSpectator()) {
                return false;
            }
            boolean isPlayerInv = originSlot.container instanceof Inventory;
            Component invTitle = isPlayerInv ? ((AbstractContainerScreenAccessor)screen).clientsort$getPlayerInventoryTitle() : screen.getTitle();
            Object object = ClientSort.getObj(originSlot, screen.getMenu());
            if (object == null) {
                return false;
            }
            @Nullable ClassPolicy policy = PolicyManager.getPolicy(object.getClass(), invTitle.getString());
            if (policy == null) break block15;
            switch (operation) {
                default: {
                    throw new MatchException(null, null);
                }
                case SORT: {
                    if (!policy.canSort()) break;
                    break block15;
                }
                case STACK_FILL: {
                    if (!policy.canStackFill()) break;
                    break block15;
                }
                case MATCH_TRANSFER: {
                    if (!policy.canMatchTransfer()) break;
                    break block15;
                }
                case TRANSFER: {
                    if (policy.canTransfer()) break block15;
                }
            }
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Operation {} is disallowed by policy for class {}!", operation.name(), policy.getClass());
            }
            return false;
        }
        if (!onlyClient && Config.options().useServerAcceleration && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(operation.type)) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Preparing server operator for {}", operation.name());
            }
            wrapper.accept(new ServerOperator(screen, originSlot, operation));
            return true;
        }
        Runnable op = () -> {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Preparing server operator for {}", operation.name());
            }
            wrapper.accept(SingleUseOperator.getClientOperator(screen, originSlot, operation));
            SingleUseOperator.endClientOp();
        };
        if (com.thelads.core.features.alwayson.clientsort.ClientSortClient.operatingClient) {
            if (com.thelads.core.features.alwayson.clientsort.ClientSortClient.clientOpQueue.offer(op)) {
                if (ClientSortClient.debug()) {
                    com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Client operation added to queue: another operation is in progress!", new Object[0]);
                }
                return true;
            }
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.warn("Client operation rejected: another operation is in progress and the queue is full!", new Object[0]);
            }
            return false;
        }
        SingleUseOperator.startClientOp();
        op.run();
        return true;
    }

    private static SingleUseOperator getClientOperator(AbstractContainerScreen<?> screen, Slot originSlot, Operation operation) {
        if (Objects.requireNonNull(Minecraft.getInstance().player).isCreative() && screen instanceof CreativeModeInventoryScreen) {
            if (ClientSortClient.debug()) {
                com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Preparing client-creative operator for {}", operation.name());
            }
            return new ClientCreativeOperator(screen, originSlot, operation);
        }
        if (ClientSortClient.debug()) {
            com.thelads.core.features.alwayson.clientsort.ClientSortClient.LOG.info("Preparing client-survival operator for {}", operation.name());
        }
        return new ClientSurvivalOperator(screen, originSlot, operation);
    }

    protected abstract void sort(SortOrder var1);

    protected abstract void fillStacks();

    protected abstract void matchTransfer();

    protected abstract void transfer();

    protected static Slot[] collectMatchingSlots(Slot[] originSlots, ItemStack[] otherStacks, boolean alwaysMatchByType, Set<Item> typeMatchItems) {
        ArrayList<Slot> slots = new ArrayList<Slot>();
        for (Slot slot : originSlots) {
            if (!SingleUseOperator.containsMatchingStack(otherStacks, slot.getItem(), alwaysMatchByType, typeMatchItems)) continue;
            slots.add(slot);
        }
        return slots.toArray(new Slot[0]);
    }

    protected static boolean containsMatchingStack(ItemStack[] stacks, ItemStack stack, boolean alwaysMatchByType, Set<Item> typeMatchItems) {
        for (ItemStack s : stacks) {
            if (!SingleUseOperator.stacksMatch(s, stack, alwaysMatchByType, typeMatchItems)) continue;
            return true;
        }
        return false;
    }

    protected static boolean stacksMatch(ItemStack a, ItemStack b, boolean alwaysMatchByType, Set<Item> typeMatchItems) {
        return ItemStack.isSameItemSameComponents((ItemStack)a, (ItemStack)b) || ItemStack.isSameItem((ItemStack)a, (ItemStack)b) && (alwaysMatchByType || typeMatchItems.contains(a.getItem()));
    }
}
