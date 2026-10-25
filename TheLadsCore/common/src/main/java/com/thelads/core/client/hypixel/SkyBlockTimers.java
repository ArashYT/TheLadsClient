package com.thelads.core.client.hypixel;

import java.util.Locale;

/**
 * Client-side countdown timers for Hypixel SkyBlock Garden:
 * 1. Composter: Cycle time tracking per organic compost, populated when checking composter inventory.
 * 2. Pest Spawns: 10-minute cooldown window while farming crops in the Garden.
 *    Source: Hypixel SkyBlock Garden Update - Chat announcement: "GROSS! A Pest has appeared in Plot - [name]!"
 *            Reset on pest spawn detection.
 */
public final class SkyBlockTimers {
    private static long composterEndNanos = 0;
    private static long pestEndNanos = 0;

    private static long cachedComposterSec = -1;
    private static String cachedComposterString = "Composter: Ready";

    private static long cachedPestSec = -1;
    private static String cachedPestString = "Pest: Ready";

    private SkyBlockTimers() {}

    public static void setComposterRemainingSeconds(int seconds) {
        cachedComposterSec = -1;
        cachedComposterString = null;
        if (seconds <= 0) {
            composterEndNanos = 0;
        } else {
            composterEndNanos = System.nanoTime() + (long) seconds * 1_000_000_000L;
        }
    }

    public static void resetPestTimer() {
        // Standard 10 minute pest spawn cooldown in the Garden (Source: Hypixel SkyBlock Garden Mechanics)
        cachedPestSec = -1;
        cachedPestString = null;
        pestEndNanos = System.nanoTime() + 600_000_000_000L;
    }

    public static void clearTimers() {
        composterEndNanos = 0;
        pestEndNanos = 0;
        cachedComposterSec = -1;
        cachedComposterString = null;
        cachedPestSec = -1;
        cachedPestString = null;
    }

    /** Formats remaining composter time or ready status. */
    public static String getComposterString(boolean preview) {
        if (preview) {
            return "Composter: 4m 12s";
        }
        if (composterEndNanos == 0) {
            return "Composter: Ready";
        }
        long remainingNanos = composterEndNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return "Composter: Done!";
        }
        long totalSeconds = remainingNanos / 1_000_000_000L;
        if (totalSeconds == cachedComposterSec && cachedComposterString != null) {
            return cachedComposterString;
        }
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        String res = "Composter: " + min + "m " + (sec < 10 ? "0" : "") + sec + "s";
        cachedComposterSec = totalSeconds;
        cachedComposterString = res;
        return res;
    }

    /** Formats remaining pest spawn timer or ready status. */
    public static String getPestString(boolean preview) {
        if (preview) {
            return "Pest: 2m 45s";
        }
        if (pestEndNanos == 0) {
            return "Pest: Ready";
        }
        long remainingNanos = pestEndNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return "Pest: Due!";
        }
        long totalSeconds = remainingNanos / 1_000_000_000L;
        if (totalSeconds == cachedPestSec && cachedPestString != null) {
            return cachedPestString;
        }
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        String res = "Pest: " + min + "m " + (sec < 10 ? "0" : "") + sec + "s";
        cachedPestSec = totalSeconds;
        cachedPestString = res;
        return res;
    }

    /**
     * Hypixel chat trigger parser.
     * Sourced triggers:
     * - "GROSS! A Pest has appeared in Plot - [name]!" (Hypixel Garden Pest spawn announcement)
     */
    public static void handleChatMessage(String cleanMessage) {
        if (cleanMessage == null) return;
        String lower = cleanMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("a pest has appeared in plot") || lower.contains("gross! a pest has appeared")) {
            resetPestTimer();
        }
    }
}
