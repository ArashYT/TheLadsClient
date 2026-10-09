package com.thelads.core.v1_20_1.hud.items;

import com.thelads.core.v1_20_1.hud.Hud1201;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/**
 * A bounded copy of a shulker box's 27 storage slots (26.3 ShulkerSummary). On 1.20.1 the contents are NBT: a box item's
 * {@code BlockEntityTag} and a placed box's saved data both hold {@code Items} with slot numbers. Empty slots keep their positions.
 */
public record ShulkerSummary1201(List<ItemStack> slots, ItemStack first, int occupied, long count, boolean uniform) {
    public static final ShulkerSummary1201 EMPTY = of(List.of());

    /** From block entity data ({@code Items}); null data is an empty box. */
    public static ShulkerSummary1201 of(CompoundTag data) {
        if (data == null || !data.contains("Items", 9)) return EMPTY;
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data, items);
        return of(items);
    }

    public static ShulkerSummary1201 of(List<ItemStack> items) {
        var slots = new ArrayList<ItemStack>(27);
        ItemStack first = ItemStack.EMPTY;
        int occupied = 0;
        long count = 0;
        boolean uniform = true;
        for (int index = 0; index < 27; index++) {
            ItemStack stack = index < items.size() ? items.get(index).copy() : ItemStack.EMPTY;
            slots.add(stack);
            if (stack.isEmpty()) continue;
            occupied++;
            count += stack.getCount();
            if (first.isEmpty()) first = stack;
            else if (!ItemStack.isSameItem(first, stack)) uniform = false;
        }
        return new ShulkerSummary1201(List.copyOf(slots), first, occupied, count, uniform);
    }

    public int freeBarWidth() { return Math.max(1, Math.round(13f * (27 - occupied) / 27)); }
    public int freeBarColor() { return 0xff000000 | Mth.hsvToRgb((27 - occupied) / 81f, 1, 1); }
    public boolean badgeVisible() { return !first.isEmpty() && (Hud1201.choice("ShulkerBoxUtils", "Display Mode", 0) == 0 || uniform); }
}
