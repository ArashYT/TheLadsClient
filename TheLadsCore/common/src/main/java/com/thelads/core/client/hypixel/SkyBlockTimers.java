package com.thelads.core.client.hypixel;

import java.util.Locale;

/**
 * Client-side countdown timers for Hypixel SkyBlock Garden:
 * 1. Jacob's Farming Contest: Fixed real-world schedule (runs :15-:35 past every hour). Needs no API.
 * 2. Composter: 10-minute cycle per organic compost, tracked via GUI / chat events.
 * 3. Pest Spawns: ~10-minute cooldown window while farming crops in the Garden.
 */
public final class SkyBlockTimers {
    private static long composterEndNanos = 0;
    private static long pestEndNanos = 0;

    private SkyBlockTimers() {}

    public static void setComposterRemainingSeconds(int seconds) {
        if (seconds <= 0) {
            composterEndNanos = 0;
        } else {
            composterEndNanos = System.nanoTime() + (long) seconds * 1_000_000_000L;
        }
    }

    public static void resetPestTimer() {
        // Standard 10 minute pest spawn cooldown in the Garden
        pestEndNanos = System.nanoTime() + 600_000_000_000L;
    }

    public static void clearTimers() {
        composterEndNanos = 0;
        pestEndNanos = 0;
    }

    /** Formats the active or upcoming Jacob's Farming Contest timer. */
    public static String getContestString(boolean preview) {
        if (preview) {
            return "Jacob: 18m 20s";
        }
        long now = System.currentTimeMillis();
        long minute = (now / 60000) % 60;
        long second = (now / 1000) % 60;
        if (minute >= 15 && minute < 35) {
            long remMin = 34 - minute;
            long remSec = 59 - second;
            return String.format(Locale.ROOT, "Jacob: ACTIVE (%dm %02ds)", remMin, remSec);
        } else {
            long waitMin = (15 - minute + 60) % 60;
            if (waitMin == 0) waitMin = 60;
            waitMin--;
            long remSec = 59 - second;
            return String.format(Locale.ROOT, "Jacob: %dm %02ds", waitMin, remSec);
        }
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
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        return String.format(Locale.ROOT, "Composter: %dm %02ds", min, sec);
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
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        return String.format(Locale.ROOT, "Pest: %dm %02ds", min, sec);
    }

    public static void handleChatMessage(String cleanMessage) {
        if (cleanMessage == null) return;
        String lower = cleanMessage.toLowerCase(Locale.ROOT);
        if (lower.contains("a pest has appeared") || lower.contains("pest appeared") || lower.contains("spotted a pest")) {
            resetPestTimer();
        } else if (lower.contains("compost complete") || lower.contains("composter produced") || lower.contains("composter finished")) {
            setComposterRemainingSeconds(600);
        }
    }
}
