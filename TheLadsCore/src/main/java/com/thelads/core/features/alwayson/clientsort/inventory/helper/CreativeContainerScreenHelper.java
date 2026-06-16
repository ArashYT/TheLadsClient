/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
 *  net.minecraft.world.entity.player.Inventory
 *  net.minecraft.world.inventory.Slot
 */
package com.thelads.core.features.alwayson.clientsort.inventory.helper;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.inventory.Scope;
import com.thelads.core.features.alwayson.clientsort.inventory.helper.ContainerScreenHelper;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class CreativeContainerScreenHelper<T extends CreativeModeInventoryScreen>
extends ContainerScreenHelper<T> {
    public CreativeContainerScreenHelper(T screen) {
        super(screen);
    }

    @Override
    public Scope getScope(Slot slot) {
        if (((CreativeModeInventoryScreen)this.screen).isInventoryOpen()) {
            return super.getScope(slot);
        }
        if (slot.container instanceof Inventory && CreativeContainerScreenHelper.isHotbarSlot(slot)) {
            return switch (Config.options().hotbarScope) {
                default -> throw new MatchException(null, null);
                case Config.Options.HotbarScope.HOTBAR, Config.Options.HotbarScope.INVENTORY -> Scope.PLAYER_INV_HOTBAR;
                case Config.Options.HotbarScope.NONE -> Scope.INVALID;
            };
        }
        return Scope.INVALID;
    }

    @Override
    public void translateSlotIds(int[] slotMapping) {
        if (!((CreativeModeInventoryScreen)this.screen).isInventoryOpen()) {
            int i = 0;
            while (i < slotMapping.length) {
                int n = i++;
                slotMapping[n] = slotMapping[n] - 9;
            }
        }
    }
}
