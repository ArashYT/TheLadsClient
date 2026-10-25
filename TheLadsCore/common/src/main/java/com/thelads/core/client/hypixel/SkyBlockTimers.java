package com.thelads.core.client.hypixel;

/**
 * Client-side countdown timers for Hypixel SkyBlock:
 * Dark Auction: Runs once every real-life hour at :55:00 (55 minutes past each hour, lasting ~5 minutes).
 * Verified authentic schedule requiring no external API key.
 */
public final class SkyBlockTimers {
    private static long cachedDaSec = -1;
    private static String cachedDaString = null;

    private SkyBlockTimers() {}

    public static void clearTimers() {
        cachedDaSec = -1;
        cachedDaString = null;
    }

    /**
     * Calculates the remaining seconds until the next Dark Auction event begins at :55:00 past the hour.
     * Returns 0 if Dark Auction is currently active (between :55:00 and :59:59).
     */
    public static long getDarkAuctionSecondsUntil(long epochMillis) {
        long totalSec = epochMillis / 1000L;
        long secIntoHour = totalSec % 3600L;
        if (secIntoHour < 0) secIntoHour += 3600L;

        long daStartSec = 55L * 60L; // 3300
        if (secIntoHour >= daStartSec) {
            return 0L; // Active
        }
        return daStartSec - secIntoHour;
    }

    /**
     * Formats the active or upcoming Dark Auction timer for the HUD.
     * Uses second-based string caching to prevent garbage allocation on the render thread.
     */
    public static String getDarkAuctionString(boolean preview) {
        if (preview) {
            return "Dark Auction: 42m 15s";
        }
        long nowMillis = System.currentTimeMillis();
        long nowSec = nowMillis / 1000L;
        if (nowSec == cachedDaSec && cachedDaString != null) {
            return cachedDaString;
        }

        String res = formatDarkAuction(nowMillis);
        cachedDaSec = nowSec;
        cachedDaString = res;
        return res;
    }

    /** Formats Dark Auction status for a given epoch timestamp. */
    static String formatDarkAuction(long epochMillis) {
        long totalSec = epochMillis / 1000L;
        long secIntoHour = totalSec % 3600L;
        if (secIntoHour < 0) secIntoHour += 3600L;

        long daStartSec = 55L * 60L; // 3300
        if (secIntoHour >= daStartSec) {
            long remActive = 3600L - secIntoHour;
            long remMin = remActive / 60L;
            long remSec = remActive % 60L;
            return "Dark Auction: ACTIVE (" + remMin + "m " + (remSec < 10 ? "0" : "") + remSec + "s)";
        } else {
            long remaining = daStartSec - secIntoHour;
            long min = remaining / 60L;
            long sec = remaining % 60L;
            return "Dark Auction: " + min + "m " + (sec < 10 ? "0" : "") + sec + "s";
        }
    }

    /** Chat trigger receiver (kept for HypixelMods event hook compatibility). */
    public static void handleChatMessage(String cleanMessage) {}
}
