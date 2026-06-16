/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.ints.IntArrays
 *  it.unimi.dsi.fastutil.objects.Object2IntMap
 *  it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.world.item.CreativeModeTabs
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 */
package com.thelads.core.features.alwayson.clientsort.order;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.order.CreativeSearchOrder;
import com.thelads.core.features.alwayson.clientsort.order.SortContext;
import com.thelads.core.features.alwayson.clientsort.order.StackComparison;
import com.thelads.core.features.alwayson.clientsort.order.StackMatcher;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public abstract class SortOrder {
    public final String name;
    public static final Map<String, SortOrder> SORT_ORDERS = new HashMap<String, SortOrder>();
    public static final SortOrder NONE = SortOrder.register(new SortOrder("none"){});
    public static final SortOrder ALPHABET = SortOrder.register(new SortOrder("alphabet"){

        @Override
        public int[] sort(int[] slotIds, ItemStack[] stacks, SortContext context) {
            String[] strings = new String[slotIds.length];
            for (int i = 0; i < slotIds.length; ++i) {
                ItemStack stack = stacks[i];
                strings[i] = stack.isEmpty() ? "" : stack.getHoverName().getString();
            }
            IntArrays.quickSort((int[])slotIds, (a, b) -> {
                int cmp = SortOrder.comparePresetPriority(stacks[a], stacks[b]);
                if (cmp != 0) {
                    return cmp;
                }
                if (strings[a].isEmpty()) {
                    if (strings[b].isEmpty()) {
                        return 0;
                    }
                    return 1;
                }
                if (strings[b].isEmpty()) {
                    return -1;
                }
                cmp = strings[a].compareToIgnoreCase(strings[b]);
                if (cmp != 0) {
                    return cmp;
                }
                return StackComparison.compareEqualItems(stacks[a], stacks[b], context);
            });
            return slotIds;
        }
    });
    public static final SortOrder CREATIVE = SortOrder.register(new SortOrder("creative"){

        @Override
        public int[] sort(int[] slotIds, ItemStack[] stacks, SortContext context) {
            int[] sortValues = new int[slotIds.length];
            if (Config.options().optimizeCreativeSorting && ClientSortClient.searchOrderUpdated) {
                Lock lock = CreativeSearchOrder.getReadLock();
                lock.lock();
                for (int i = 0; i < stacks.length; ++i) {
                    sortValues[i] = CreativeSearchOrder.getPosition(stacks[i]);
                }
                lock.unlock();
            } else {
                Collection displayStacks;
                if (Config.options().optimizeCreativeSorting) {
                    CreativeSearchOrder.tryRefreshStackPositionMap();
                }
                ArrayList displayStackList = (displayStacks = CreativeModeTabs.searchTab().getDisplayItems()) instanceof List ? (ArrayList)displayStacks : new ArrayList(displayStacks);
                Object2IntOpenHashMap lookup = new Object2IntOpenHashMap(stacks.length);
                for (int i = 0; i < stacks.length; ++i) {
                    ItemStack stack = stacks[i];
                    sortValues[i] = lookup.computeIfAbsent((Object)StackMatcher.of(stack), arg_0 -> lambda$sort$0(displayStackList, (Object2IntMap)lookup, stack, arg_0));
                }
            }
            SortOrder.sortByValues(slotIds, sortValues, stacks, context);
            return slotIds;
        }

        private static /* synthetic */ int lambda$sort$0(List displayStackList, Object2IntMap lookup, ItemStack stack, Object matcher) {
            int index = displayStackList.indexOf(matcher);
            if (index != -1) {
                return index;
            }
            return lookup.computeIfAbsent((Object)StackMatcher.ignoreNbt(stack), altMatcher -> {
                int plainIndex = displayStackList.indexOf(altMatcher);
                if (plainIndex == -1) {
                    return Integer.MAX_VALUE;
                }
                return plainIndex;
            });
        }
    });
    public static final SortOrder QUANTITY = SortOrder.register(new SortOrder("quantity"){

        @Override
        public int[] sort(int[] slotIds, ItemStack[] stacks, SortContext context) {
            HashMap<Item, Integer> itemTotalAmountMap = new HashMap<Item, Integer>();
            for (ItemStack stack : stacks) {
                if (stack.isEmpty()) continue;
                itemTotalAmountMap.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            IntArrays.quickSort((int[])slotIds, (a, b) -> {
                int cmp = SortOrder.comparePresetPriority(stacks[a], stacks[b]);
                if (cmp != 0) {
                    return cmp;
                }
                ItemStack stackA = stacks[a];
                ItemStack stackB = stacks[b];
                if (stackA.isEmpty()) {
                    return stackB.isEmpty() ? 0 : 1;
                }
                if (stackB.isEmpty()) {
                    return -1;
                }
                Integer amountA = (Integer)itemTotalAmountMap.get(stackA.getItem());
                Integer amountB = (Integer)itemTotalAmountMap.get(stackB.getItem());
                cmp = Integer.compare(amountB, amountA);
                if (cmp != 0) {
                    return cmp;
                }
                if (ItemStack.isSameItemSameComponents((ItemStack)stackA, (ItemStack)stackB)) {
                    return StackComparison.compareEqualItems(stackA, stackB, context);
                }
                return StackComparison.compareEqualItems(stackA.copyWithCount(1), stackB.copyWithCount(1), context);
            });
            return slotIds;
        }
    });
    public static final SortOrder RAW_ID = SortOrder.register(new SortOrder("rawId"){

        @Override
        public int[] sort(int[] slotIds, ItemStack[] stacks, SortContext context) {
            int[] values = Arrays.stream(stacks).mapToInt(stack -> stack.isEmpty() ? Integer.MAX_VALUE : BuiltInRegistries.ITEM.getId(stack.getItem())).toArray();
            SortOrder.sortByValues(slotIds, values, stacks, context);
            return slotIds;
        }
    });

    protected SortOrder(String name) {
        this.name = name;
    }

    public int[] sort(int[] slotIds, ItemStack[] stacks, SortContext context) {
        return slotIds;
    }

    public static <T extends SortOrder> T register(T sortOrder) {
        SORT_ORDERS.put(sortOrder.name, sortOrder);
        return sortOrder;
    }

    private static void sortByValues(int[] sortIds, int[] values, ItemStack[] stacks, SortContext context) {
        IntArrays.quickSort((int[])sortIds, (a, b) -> {
            int cmp = SortOrder.comparePresetPriority(stacks[a], stacks[b]);
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(values[a], values[b]);
            if (cmp != 0) {
                return cmp;
            }
            return StackComparison.compareEqualItems(stacks[a], stacks[b], context);
        });
    }

    private static int comparePresetPriority(ItemStack stackA, ItemStack stackB) {
        Integer idxB;
        Integer idxA;
        if (stackA.isEmpty()) {
            if (stackB.isEmpty()) {
                return 0;
            }
            return 1;
        }
        if (stackB.isEmpty()) {
            return -1;
        }
        Item a = stackA.getItem();
        Item b = stackB.getItem();
        if (Config.options().useStartOverrides) {
            idxA = Config.options().startOverrideMap.get(a);
            idxB = Config.options().startOverrideMap.get(b);
            if (idxA != null || idxB != null) {
                if (idxA != null && idxB != null) {
                    return Integer.compare(idxA, idxB);
                }
                return idxA != null ? -1 : 1;
            }
        }
        if (Config.options().useEndOverrides) {
            idxA = Config.options().endOverrideMap.get(a);
            idxB = Config.options().endOverrideMap.get(b);
            if (idxA != null || idxB != null) {
                if (idxA != null && idxB != null) {
                    return Integer.compare(idxA, idxB);
                }
                return idxA != null ? 1 : -1;
            }
        }
        return 0;
    }
}
