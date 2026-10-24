package com.thelads.core.client.hypixel;

import java.util.HashSet;
import java.util.Set;

/**
 * OpenedChestHighlight: Highlights / dims chests already opened in SkyWars, Blitz SG, and UHC.
 * Uses packed coordinates to prevent object allocations during rendering.
 * Resets on world/game change.
 */
public final class OpenedChestHighlight {
    public static final String NAME = "Opened Chest Highlight";
    private static final Set<Long> OPENED_CHESTS = new HashSet<>();

    private OpenedChestHighlight() {}

    public static boolean isEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(NAME);
    }

    private static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z & 0x3FFFFFF);
    }

    public static void onChestOpened(int x, int y, int z) {
        if (!isEnabled()) return;
        synchronized (OPENED_CHESTS) {
            OPENED_CHESTS.add(packPos(x, y, z));
        }
    }

    public static void recordChestOpened(int x, int y, int z) {
        onChestOpened(x, y, z);
    }

    public static boolean isOpened(int x, int y, int z) {
        if (!isEnabled()) return false;
        synchronized (OPENED_CHESTS) {
            return OPENED_CHESTS.contains(packPos(x, y, z));
        }
    }

    public static int count() {
        synchronized (OPENED_CHESTS) {
            return OPENED_CHESTS.size();
        }
    }

    public static void clear() {
        synchronized (OPENED_CHESTS) {
            OPENED_CHESTS.clear();
        }
    }
}
