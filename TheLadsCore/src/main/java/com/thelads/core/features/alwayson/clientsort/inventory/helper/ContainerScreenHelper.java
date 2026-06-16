/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
 *  net.minecraft.world.Container
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.ticks.ContainerSingleItem
 */
package com.thelads.core.features.alwayson.clientsort.inventory.helper;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.inventory.Scope;
import com.thelads.core.features.alwayson.clientsort.inventory.helper.CreativeContainerScreenHelper;
import com.thelads.core.features.alwayson.clientsort.util.inject.ISlot;
import java.lang.runtime.SwitchBootstraps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.ticks.ContainerSingleItem;

public class ContainerScreenHelper<T extends AbstractContainerScreen<?>> {
    protected final T screen;

    protected ContainerScreenHelper(T screen) {
        this.screen = screen;
    }

    public static <T extends AbstractContainerScreen<?>> ContainerScreenHelper<T> of(T screen) {
        if (screen instanceof CreativeModeInventoryScreen) {
            CreativeModeInventoryScreen creativeScreen = (CreativeModeInventoryScreen)screen;
            return (ContainerScreenHelper<T>)(Object)new CreativeContainerScreenHelper<CreativeModeInventoryScreen>(creativeScreen);
        }
        return new ContainerScreenHelper<T>(screen);
    }

    public static boolean isHotbarSlot(Slot slot) {
        return ((ISlot)slot).clientsort$getIndexInContainer() < 9;
    }

    public static boolean isExtraSlot(Slot slot) {
        return ((ISlot)slot).clientsort$getIndexInContainer() > 35;
    }

    public Scope getScope(Slot slot) {
        if (slot.isFake()) {
            return Scope.INVALID;
        }
        Container container = slot.container;
        int n = 0;
        if (container instanceof ContainerSingleItem) {
            return Scope.INVALID;
        } else if (container instanceof Inventory) {
            boolean mergeWithHotbar = false;
            if (ContainerScreenHelper.isExtraSlot(slot)) {
                switch (Config.options().extraSlotScope) {
                    case HOTBAR: {
                        mergeWithHotbar = true;
                        break;
                    }
                    case EXTRA: {
                        return Scope.PLAYER_INV_EXTRA;
                    }
                    case NONE: {
                        return Scope.INVALID;
                    }
                }
            }
            if (mergeWithHotbar || ContainerScreenHelper.isHotbarSlot(slot)) {
                switch (Config.options().hotbarScope) {
                    case HOTBAR: {
                        return Scope.PLAYER_INV_HOTBAR;
                    }
                    case NONE: {
                        return Scope.INVALID;
                    }
                }
            }
            return Scope.PLAYER_INV;
        } else {
            return Scope.CONTAINER_INV;
        }
    }

    public List<Slot> getLargestSlotGroup(Scope scope) {
        List<Slot> slots = new ArrayList<Slot>();
        for (List<Slot> group : this.getSlotGroups(scope)) {
            if (group.size() <= slots.size()) continue;
            slots = group;
        }
        return slots;
    }

    public List<Slot> getGroupForSlot(Slot slot, Scope scope) {
        if (scope == Scope.PLAYER_INV || scope == Scope.PLAYER_INV_HOTBAR) {
            List<Slot> slots = this.getAllSlots(slot.container, scope);
            if (slots.contains(slot)) {
                return slots;
            }
        } else {
            for (List<Slot> group : this.getSlotGroups(scope)) {
                if (!group.contains(slot)) continue;
                return group;
            }
        }
        return new ArrayList<Slot>();
    }

    public List<List<Slot>> getSlotGroups(Scope scope) {
        ArrayList<List<Slot>> slotGroups = new ArrayList<List<Slot>>();
        ArrayList<Slot> currentGroup = new ArrayList<Slot>();
        int lastIdx = Integer.MAX_VALUE;
        Container lastContainer = null;
        for (Slot slot : this.screen.getMenu().slots) {
            if (slot.container == null || !this.getScope(slot).equals((Object)scope)) continue;
            int slotIdx = ((ISlot)slot).clientsort$getIndexInContainer();
            if (slotIdx <= lastIdx || slot.container != lastContainer) {
                slotGroups.add(currentGroup);
                currentGroup = new ArrayList();
            }
            currentGroup.add(slot);
            lastIdx = slotIdx;
            lastContainer = slot.container;
        }
        if (!currentGroup.isEmpty()) {
            slotGroups.add(currentGroup);
        }
        return slotGroups;
    }

    public List<Slot> getAllSlots(Container container, Scope scope) {
        ArrayList<Slot> slots = new ArrayList<Slot>();
        for (Slot slot : this.screen.getMenu().slots) {
            if (slot.container == null || !this.getScope(slot).equals((Object)scope) || slot.container != container) continue;
            slots.add(slot);
        }
        return slots;
    }

    public void translateSlotIds(int[] slotMapping) {
    }
}
