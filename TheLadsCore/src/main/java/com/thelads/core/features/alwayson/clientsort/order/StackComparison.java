/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$TooltipContext
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.item.TooltipFlag$Default
 *  net.minecraft.world.item.component.DyedItemColor
 *  net.minecraft.world.level.Level
 */
package com.thelads.core.features.alwayson.clientsort.order;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.order.SortContext;
import java.awt.Color;
import java.util.Iterator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

public class StackComparison {
    public static int compareEqualItems(ItemStack a, ItemStack b, SortContext context) {
        int cmp = Integer.compare(b.getCount(), a.getCount());
        if (cmp != 0) {
            return cmp;
        }
        return StackComparison.compareEqualItems2(a, b, context);
    }

    private static int compareEqualItems2(ItemStack a, ItemStack b, SortContext context) {
        if (StackComparison.hasCustomHoverName(a)) {
            if (!StackComparison.hasCustomHoverName(b)) {
                return -1;
            }
            return StackComparison.compareEqualItems3(a, b, context);
        }
        if (StackComparison.hasCustomHoverName(b)) {
            return 1;
        }
        return StackComparison.compareEqualItems3(a, b, context);
    }

    private static boolean hasCustomHoverName(ItemStack itemStack) {
        return itemStack.get(DataComponents.CUSTOM_NAME) != null;
    }

    private static int compareEqualItems3(ItemStack a, ItemStack b, SortContext context) {
        Iterator tooltipsA = a.getTooltipLines(Item.TooltipContext.of((Level)context.level), null, (TooltipFlag)TooltipFlag.Default.NORMAL).iterator();
        Iterator tooltipsB = b.getTooltipLines(Item.TooltipContext.of((Level)context.level), null, (TooltipFlag)TooltipFlag.Default.NORMAL).iterator();
        while (tooltipsA.hasNext()) {
            if (!tooltipsB.hasNext()) {
                return 1;
            }
            int cmp = ((Component)tooltipsA.next()).getString().compareToIgnoreCase(((Component)tooltipsB.next()).getString());
            if (cmp == 0) continue;
            return cmp;
        }
        if (tooltipsB.hasNext()) {
            return -1;
        }
        return StackComparison.compareEqualItems4(a, b, context);
    }

    private static int compareEqualItems4(ItemStack a, ItemStack b, SortContext context) {
        Item item = a.getItem();
        if (item.getDefaultInstance().has(DataComponents.DYED_COLOR)) {
            float[] hsbB;
            int colorA = DyedItemColor.getOrDefault((ItemStack)a, (int)-6265536);
            int colorB = DyedItemColor.getOrDefault((ItemStack)b, (int)-6265536);
            float[] hsbA = Color.RGBtoHSB(colorA >> 16 & 0xFF, colorA >> 8 & 0xFF, colorA & 0xFF, null);
            int cmp = Float.compare(hsbA[0], (hsbB = Color.RGBtoHSB(colorB >> 16 & 0xFF, colorB >> 8 & 0xFF, colorB & 0xFF, null))[0]);
            if (cmp != 0) {
                return cmp;
            }
            cmp = Float.compare(hsbA[1], hsbB[1]);
            if (cmp != 0) {
                return cmp;
            }
            cmp = Float.compare(hsbA[2], hsbB[2]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return StackComparison.compareEqualItems5(a, b, context);
    }

    private static int compareEqualItems5(ItemStack a, ItemStack b, SortContext context) {
        return Integer.compare(a.getDamageValue(), b.getDamageValue());
    }
}
