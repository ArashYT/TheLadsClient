/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2IntMap
 *  it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
 *  net.minecraft.client.Minecraft
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.world.flag.FeatureFlagSet
 *  net.minecraft.world.item.CreativeModeTabs
 *  net.minecraft.world.item.ItemStack
 */
package com.thelads.core.features.alwayson.clientsort.order;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.order.StackMatcher;
import com.thelads.core.mixin.alwayson.clientsort.client.accessor.CreativeModeTabsAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

public class CreativeSearchOrder {
    private static final Object2IntMap<StackMatcher> stackPositionMap = new Object2IntOpenHashMap();
    private static final ReadWriteLock stackPositionMapLock;

    public static Lock getReadLock() {
        return stackPositionMapLock.readLock();
    }

    public static int getPosition(ItemStack stack) {
        int pos = stackPositionMap.getInt((Object)StackMatcher.of(stack));
        if (pos == Integer.MAX_VALUE) {
            pos = stackPositionMap.getInt((Object)StackMatcher.ignoreNbt(stack));
        }
        return pos;
    }

    public static void tryRefreshStackPositionMap() {
        if (Config.options().optimizeCreativeSorting) {
            if (ClientSortClient.emiReloading) {
                ClientSortClient.updateBlockedByEmi = true;
                ClientSortClient.LOG.info("Search order update blocked by EMI reload, waiting...", new Object[0]);
            } else {
                CreativeSearchOrder.refreshStackPositionMap();
            }
        } else {
            Lock lock = stackPositionMapLock.writeLock();
            lock.lock();
            stackPositionMap.clear();
            lock.unlock();
        }
    }

    private static void refreshStackPositionMap() {
        List<ItemStack> displayStacks;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        FeatureFlagSet enabledFeatures = mc.level.enabledFeatures();
        try {
            CreativeModeTabs.tryRebuildTabContents((FeatureFlagSet)enabledFeatures, (boolean)true, (HolderLookup.Provider)mc.level.registryAccess());
            displayStacks = CreativeModeTabs.searchTab().getDisplayItems().stream().map(ItemStack::copy).toList();
        }
        finally {
            CreativeModeTabsAccessor.clientsort$setCachedParameters(null);
        }
        new Thread(() -> {
            Lock lock = stackPositionMapLock.writeLock();
            try {
                lock.lock();
                stackPositionMap.clear();
                int i = 0;
                for (ItemStack stack : displayStacks) {
                    StackMatcher plainMatcher = StackMatcher.ignoreNbt(stack);
                    if (!stack.hasFoil() || !stackPositionMap.containsKey((Object)plainMatcher)) {
                        stackPositionMap.put(plainMatcher, i);
                        ++i;
                    }
                    stackPositionMap.put(StackMatcher.of(stack), i);
                    ++i;
                }
            }
            finally {
                lock.unlock();
            }
        }, "ClientSort: creative sort builder").start();
    }

    static {
        stackPositionMap.defaultReturnValue(Integer.MAX_VALUE);
        stackPositionMapLock = new ReentrantReadWriteLock();
    }
}
