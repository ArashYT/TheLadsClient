package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ElytraSwapModule;
import java.util.List;
import java.util.Locale;

/**
 * Elytra swap hotkey logic (parity-1023).
 * Anti-ban: Gated by HypixelSafetyManager. Must never click with no screen open on a server unless server rules allow it.
 */
public final class ElytraSwap {
    public static final String ELYTRA_ID = "minecraft:elytra";
    public static final int CHESTPLATE_SLOT = 6; // Armor slot in player container (inventoryMenu)

    private static ElytraSwapModule cachedModule;

    private ElytraSwap() {}

    public static ElytraSwapModule module() {
        ElytraSwapModule m = cachedModule;
        if (m == null) {
            if (ModuleManager.getInstance().getModule(ElytraSwapModule.NAME) instanceof ElytraSwapModule found) {
                cachedModule = m = found;
            }
        }
        return m;
    }

    /**
     * True only if module is enabled and HypixelSafetyManager permits it.
     * On unknown/banned multiplayer servers, this returns false to prevent bans.
     */
    public static boolean isAllowed() {
        ElytraSwapModule m = module();
        return m != null && m.isEnabled() && HypixelSafetyManager.isAllowed(ElytraSwapModule.NAME);
    }

    /**
     * Pure logic helper to determine target slot to swap with chest armor slot.
     * @param wearingElytra true if currently wearing elytra in armor slot
     * @param inventoryItemIds list of item registry IDs in player inventory slots (e.g. slots 9..44)
     * @return index in inventoryItemIds to swap with, or -1 if no matching item found
     */
    public static int findSwapSlot(boolean wearingElytra, List<String> inventoryItemIds) {
        if (inventoryItemIds == null || inventoryItemIds.isEmpty()) return -1;

        for (int i = 0; i < inventoryItemIds.size(); i++) {
            String id = inventoryItemIds.get(i);
            if (id == null) continue;
            String lower = id.toLowerCase(Locale.ROOT);
            if (wearingElytra) {
                // Look for chestplate
                if (lower.contains("chestplate")) {
                    return i;
                }
            } else {
                // Look for elytra
                if (ELYTRA_ID.equals(lower) || lower.endsWith(":elytra")) {
                    return i;
                }
            }
        }
        return -1;
    }
}
