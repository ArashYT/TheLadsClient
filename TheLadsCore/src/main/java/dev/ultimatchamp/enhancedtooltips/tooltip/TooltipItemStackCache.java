/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Nullable
 */
package dev.ultimatchamp.enhancedtooltips.tooltip;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class TooltipItemStackCache {
    private static ItemStack cache = ItemStack.EMPTY;

    public static void saveItemStack(ItemStack stack) {
        cache = stack;
    }

    @Nullable
    public static ItemStack getItemStack() {
        return cache;
    }
}

