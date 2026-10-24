package com.thelads.core.client;

/**
 * Handles confirmation prompt before disconnecting from a multiplayer server.
 */
public final class ConfirmDisconnectHelper {
    private static long confirmExpireMillis = 0L;

    private ConfirmDisconnectHelper() {}

    /**
     * Checks if the click confirms the disconnect or if a prompt should be displayed first.
     * Returns true if disconnect is confirmed and should proceed, false if waiting for confirmation.
     */
    public static boolean onClickDisconnect(long nowMillis, long timeoutMillis, boolean isMultiplayer, boolean moduleEnabled) {
        if (!moduleEnabled || !isMultiplayer) {
            return true; // Disconnect immediately if singleplayer or disabled
        }
        if (nowMillis <= confirmExpireMillis) {
            // Confirmed!
            confirmExpireMillis = 0L;
            return true;
        }
        // First click: arm confirmation
        confirmExpireMillis = nowMillis + timeoutMillis;
        return false;
    }

    public static boolean isConfirming(long nowMillis) {
        return nowMillis <= confirmExpireMillis;
    }

    public static void reset() {
        confirmExpireMillis = 0L;
    }
}
