/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Objects
 *  net.minecraft.core.component.DataComponentMap
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.order;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.google.common.base.Objects;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StackMatcher {
    @NotNull
    private final Item item;
    @Nullable
    private final DataComponentMap components;

    private StackMatcher(@NotNull Item item, @Nullable DataComponentMap components) {
        this.item = item;
        this.components = components;
    }

    public static StackMatcher of(@NotNull ItemStack stack) {
        return new StackMatcher(stack.getItem(), stack.getComponents());
    }

    public static StackMatcher ignoreNbt(@NotNull ItemStack stack) {
        return new StackMatcher(stack.getItem(), null);
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof StackMatcher) {
            StackMatcher other = (StackMatcher)obj;
            return this.item == other.item && java.util.Objects.equals(this.components, other.components);
        }
        if (obj instanceof ItemStack) {
            ItemStack stack = (ItemStack)obj;
            return this.item == stack.getItem() && (this.components == null || java.util.Objects.equals(this.components, stack.getComponents()));
        }
        if (obj instanceof Item) {
            Item objItem = (Item)obj;
            return this.item == objItem && this.components == null;
        }
        return false;
    }

    public int hashCode() {
        return java.util.Objects.hash(this.item, this.components);
    }
}
