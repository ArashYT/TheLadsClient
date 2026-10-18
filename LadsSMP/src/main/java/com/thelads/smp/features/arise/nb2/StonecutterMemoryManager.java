package com.thelads.smp.features.arise.nb2;

import net.minecraft.world.item.Item;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the last selected recipe index for stonecutter inputs, automatically
 * restoring the selection when a matching stone item is inserted (Stonecutter Memory).
 */
public final class StonecutterMemoryManager {
    private static final Map<Item, Integer> LAST_RECIPES = new ConcurrentHashMap<>();

    private StonecutterMemoryManager() {}

    public static boolean isStonecutterMemoryEnabled() {
        return NB2Config.get().stonecutterMemory;
    }

    public static void recordSelection(Item inputItem, int recipeIndex) {
        if (!isStonecutterMemoryEnabled() || inputItem == null || recipeIndex < 0) {
            return;
        }
        LAST_RECIPES.put(inputItem, recipeIndex);
    }

    public static int getRememberedIndex(Item inputItem) {
        if (!isStonecutterMemoryEnabled() || inputItem == null) {
            return -1;
        }
        return LAST_RECIPES.getOrDefault(inputItem, -1);
    }

    public static void clear() {
        LAST_RECIPES.clear();
    }
}
