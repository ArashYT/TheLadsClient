/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 *  net.minecraft.world.inventory.Slot
 *  net.minecraft.world.level.Level
 */
package com.thelads.core.features.alwayson.clientsort.inventory.operator.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.Operation;
import com.thelads.core.features.alwayson.clientsort.inventory.operator.SingleUseOperator;
import com.thelads.core.features.alwayson.clientsort.order.SortContext;
import com.thelads.core.features.alwayson.clientsort.order.SortOrder;
import com.thelads.core.features.alwayson.clientsort.util.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;

public abstract class ClientOperator
extends SingleUseOperator {
    public ClientOperator(AbstractContainerScreen<?> screen, Slot originSlot, Operation op) {
        super(screen, originSlot, op);
    }

    @Override
    protected void sort(SortOrder sortOrder) {
        if (sortOrder.equals(SortOrder.NONE)) {
            return;
        }
        this.collect();
        int[] key = new int[this.originScopeSlots.length];
        for (int i = 0; i < key.length; ++i) {
            key[i] = i;
        }
        key = sortOrder.sort(key, this.originScopeStacks, new SortContext((Level)Minecraft.getInstance().level));
        boolean playSound = SoundManager.shouldPlaySortingSounds();
        if (playSound) {
            SoundManager.resetForCount(SoundManager.estimateSortSounds(this.originScopeStacks));
        }
        this.sort(key, playSound);
    }

    protected abstract void collect();

    protected abstract void sort(int[] var1, boolean var2);
}
