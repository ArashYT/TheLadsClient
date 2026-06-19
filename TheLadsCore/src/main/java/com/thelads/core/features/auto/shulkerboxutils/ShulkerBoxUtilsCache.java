package com.thelads.core.features.auto.shulkerboxutils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * In-memory cache of the first item held by each placed shulker box, keyed by world position.
 * Ported from shulkerboxutils 1.3.0 (the world-icon feature) minus the on-disk persistence.
 */
public final class ShulkerBoxUtilsCache {
    /** First non-empty item seen in the box at this position. */
    public static final Map<BlockPos, ItemStack> ITEMS = new ConcurrentHashMap<>();
    /**
     * Positions whose contents were captured from an opened screen and should NOT be
     * overwritten by the renderer (which can only see synced contents).
     */
    public static final Set<BlockPos> SCREEN_AUTHORITATIVE = ConcurrentHashMap.newKeySet();
    /** Position of the shulker box the player most recently interacted with. */
    public static volatile BlockPos pendingPos;

    private ShulkerBoxUtilsCache() {
    }
}
