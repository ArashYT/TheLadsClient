package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LevelHead displays a player's Hypixel Network Level above their head / nametag.
 * Gated to Hypixel only; levels are cached in memory to eliminate per-frame allocations.
 */
public final class LevelHead {
    public static final String NAME = "Level Head";
    private static final Map<String, Integer> LEVEL_CACHE = new ConcurrentHashMap<>();

    private LevelHead() {}

    public static boolean isEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(NAME);
    }

    /**
     * Converts raw network experience into Hypixel network level using Hypixel's official formula:
     * level = (sqrt(2 * exp + 30625) - 175) / 50 + 1 (starts at 1).
     */
    public static int expToLevel(double exp) {
        if (exp <= 0) return 1;
        return Math.max(1, (int) Math.floor((Math.sqrt(2.0 * exp + 30625.0) - 175.0) / 50.0) + 1);
    }

    /**
     * Color coding for Hypixel Network Level brackets.
     */
    public static String getLevelColor(int level) {
        if (level < 100) return "§7";       // 1-99: Gray
        if (level < 200) return "§f";       // 100-199: White
        if (level < 300) return "§6";       // 200-299: Gold
        if (level < 400) return "§b";       // 300-399: Aqua
        if (level < 500) return "§2";       // 400-499: Dark Green
        if (level < 600) return "§4";       // 500-599: Dark Red
        if (level < 700) return "§d";       // 600-699: Light Purple
        if (level < 800) return "§9";       // 700-799: Blue
        if (level < 900) return "§5";       // 800-899: Purple
        if (level < 1000) return "§c";      // 900-999: Red
        return "§6§l";                      // 1000+: Bold Gold
    }

    public static Integer getLevel(String playerName) {
        if (playerName == null) return null;
        return LEVEL_CACHE.get(playerName.toLowerCase(Locale.ROOT));
    }

    public static void setLevel(String playerName, int level) {
        if (playerName != null) {
            LEVEL_CACHE.put(playerName.toLowerCase(Locale.ROOT), level);
        }
    }

    public static void clearCache() {
        LEVEL_CACHE.clear();
    }

    /**
     * Formats the level head prefix for a player nametag (e.g. "§6[Level: 142]§r ").
     * Returns null if LevelHead is disabled, not on Hypixel, or player's level is unknown.
     */
    public static String formatLevelTag(String playerName) {
        if (!isEnabled() || playerName == null) return null;
        Integer level = getLevel(playerName);
        if (level == null) return null;
        String prefix = HypixelMods.levelHeadPrefix();
        return getLevelColor(level) + "[" + prefix + level + "]§r ";
    }
}
