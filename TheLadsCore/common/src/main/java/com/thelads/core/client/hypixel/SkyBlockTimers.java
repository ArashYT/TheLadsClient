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

    private static long cachedJacobEpochSec = -1;
    private static String cachedJacobString = "Jacob: Ready";

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
        // Standard 10 minute pest spawn cooldown in the Garden
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
        cachedJacobEpochSec = -1;
        cachedJacobString = null;
    }

    /** Formats the active or upcoming Jacob's Farming Contest timer. */
    public static String getContestString(boolean preview) {
        if (preview) {
            return "Jacob: 18m 20s";
        }
        long now = System.currentTimeMillis();
        long epochSec = now / 1000L;
        if (epochSec == cachedJacobEpochSec && cachedJacobString != null) {
            return cachedJacobString;
        }
        long minute = (epochSec / 60) % 60;
        long second = epochSec % 60;
        String res;
        if (minute >= 15 && minute < 35) {
            long remMin = 34 - minute;
            long remSec = 59 - second;
            res = "Jacob: ACTIVE (" + remMin + "m " + (remSec < 10 ? "0" : "") + remSec + "s)";
        } else {
            long waitMin = (15 - minute + 60) % 60;
            if (waitMin == 0) waitMin = 60;
            waitMin--;
            long remSec = 59 - second;
            res = "Jacob: " + waitMin + "m " + (remSec < 10 ? "0" : "") + remSec + "s";
        }
        cachedJacobEpochSec = epochSec;
        cachedJacobString = res;
        return res;
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
