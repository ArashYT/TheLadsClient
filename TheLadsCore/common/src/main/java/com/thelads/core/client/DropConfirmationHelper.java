package com.thelads.core.client;

/**
 * Handles confirmation prompt before dropping valuable items with the drop key (Q).
 */
public final class DropConfirmationHelper {
    private static long confirmExpireMillis = 0L;

    private DropConfirmationHelper() {}

    /**
     * Checks if dropping this valuable item is confirmed.
     * Returns true if drop should proceed, false if cancelled/waiting for confirmation.
     */
    public static boolean checkCanDrop(long nowMillis, long timeoutMillis, boolean isValuable, boolean moduleEnabled) {
        if (!moduleEnabled || !isValuable) {
            return true; // Not valuable or module disabled: drop immediately
        }
        if (nowMillis <= confirmExpireMillis) {
            // Confirmed second press!
            confirmExpireMillis = 0L;
            return true;
        }
        // First press: arm confirmation window
        confirmExpireMillis = nowMillis + timeoutMillis;
        return false;
    }

    public static boolean isValuableName(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("diamond") || lower.contains("netherite") || lower.contains("emerald")
            || lower.contains("sword") || lower.contains("pickaxe") || lower.contains("axe")
            || lower.contains("shovel") || lower.contains("hoe") || lower.contains("helmet")
            || lower.contains("chestplate") || lower.contains("leggings") || lower.contains("boots")
            || lower.contains("elytra") || lower.contains("totem") || lower.contains("trident")
            || lower.contains("mace") || lower.contains("bow");
    }

    public static void reset() {
        confirmExpireMillis = 0L;
    }
}
