package com.thelads.core.v26_2.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** A bounded copy of the component's 27 storage slots. Empty slots retain their original positions. */
public record ShulkerSummary(List<ItemStack> slots, ItemStack first, int occupied, long count, boolean uniform) {
    public static final ShulkerSummary EMPTY = of(List.of());
    private static final Map<ItemContainerContents, ShulkerSummary> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    public static ShulkerSummary of(ItemContainerContents contents) {
        if (contents == null) return EMPTY;
        return CACHE.computeIfAbsent(contents, c -> build(c.itemCopies().limit(27).toList(), false));
    }

    public static ShulkerSummary of(List<ItemStack> items) {
        return build(items, true);
    }

    private static ShulkerSummary build(List<ItemStack> items, boolean copy) {
        var slots = new ArrayList<ItemStack>(27);
        ItemStack first = ItemStack.EMPTY;
        int occupied = 0;
        long count = 0;
        boolean uniform = true;
        for (int index = 0; index < 27; index++) {
            ItemStack stack = ItemStack.EMPTY;
            if (index < items.size()) {
                ItemStack item = items.get(index);
                if (item != null) stack = copy ? item.copy() : item;
            }
            slots.add(stack);
            if (stack.isEmpty()) continue;
            occupied++;
            count += stack.getCount();
            if (first.isEmpty()) first = stack;
            else if (!ItemStack.isSameItem(first, stack)) uniform = false;
        }
        return new ShulkerSummary(List.copyOf(slots), first, occupied, count, uniform);
    }

    public int freeBarWidth() { return Math.max(1, Math.round(13f * (27 - occupied) / 27)); }
    public int freeBarColor() { return 0xff000000 | net.minecraft.util.Mth.hsvToRgb((27 - occupied) / 81f, 1, 1); }
    public boolean badgeVisible() { return !first.isEmpty() && (NativeQualityOfLife.choice("ShulkerBoxUtils", "Display Mode", 0) == 0 || uniform); }
}
