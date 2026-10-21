package com.thelads.core.client.hypixel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bed Wars map build height limits and runtime learning.
 * Sources:
 * - Hypixel Forums BedWars Map Guides (https://hypixel.net/threads/2395666/)
 * - Community Map Height Databases (https://maps.pinkulu.com/)
 */
public final class BedWarsHeight {
    public static final int DEFAULT_LIMIT = 110;
    private static final Map<String, Integer> KNOWN_LIMITS = new HashMap<>();
    private static final Map<String, Integer> LEARNED_LIMITS = new HashMap<>();
    private static final Pattern LIMIT_REACHED = Pattern.compile("(?i).*\\b(?:Build height limit reached!?|You have reached the build height limit!?|Build limit reached!?).*");

    static {
        // Known map build height limits (Y level)
        KNOWN_LIMITS.put("lighthouse", 111);
        KNOWN_LIMITS.put("speedway", 91);
        KNOWN_LIMITS.put("airshow", 101);
        KNOWN_LIMITS.put("amazon", 94);
        KNOWN_LIMITS.put("apollo", 99);
        KNOWN_LIMITS.put("aquarium", 111);
        KNOWN_LIMITS.put("ashfire", 106);
        KNOWN_LIMITS.put("orchestra", 105);
        KNOWN_LIMITS.put("pernicious", 113);
        KNOWN_LIMITS.put("rooftop", 108);
        KNOWN_LIMITS.put("zarzul", 100);
        KNOWN_LIMITS.put("cascade", 100);
        KNOWN_LIMITS.put("gateway", 105);
        KNOWN_LIMITS.put("steampunk", 105);
        KNOWN_LIMITS.put("pavilion", 115);
        KNOWN_LIMITS.put("babylon", 115);
        KNOWN_LIMITS.put("fang outpost", 105);
        KNOWN_LIMITS.put("dragonstar", 108);
        KNOWN_LIMITS.put("dreamgrove", 110);
        KNOWN_LIMITS.put("invasion", 115);
        KNOWN_LIMITS.put("waterfall", 110);
        KNOWN_LIMITS.put("hollow", 100);
        KNOWN_LIMITS.put("solace", 110);
        KNOWN_LIMITS.put("skyline", 110);
        KNOWN_LIMITS.put("acropolis", 105);
        KNOWN_LIMITS.put("ironstone", 105);
    }

    private BedWarsHeight() {}

    /**
     * Returns the build height limit for the specified map name, checking learned limits first,
     * then known static limits, falling back to the default limit (110).
     */
    public static int getLimit(String mapName) {
        if (mapName == null || mapName.isEmpty()) return DEFAULT_LIMIT;
        String key = mapName.toLowerCase(Locale.ROOT).trim();
        Integer learned = LEARNED_LIMITS.get(key);
        if (learned != null) return learned;
        Integer known = KNOWN_LIMITS.get(key);
        if (known != null) return known;
        return DEFAULT_LIMIT;
    }

    /**
     * Checks if a server chat message indicates the build height limit has been reached.
     */
    public static boolean isLimitReachedMessage(String message) {
        if (message == null) return false;
        String plain = HypixelChat.plain(message).trim();
        return LIMIT_REACHED.matcher(plain).matches();
    }

    /**
     * Learns and stores the build height limit for a given map name.
     */
    public static boolean learnLimit(String mapName, int y) {
        if (mapName == null || mapName.trim().isEmpty() || y <= 0) return false;
        LEARNED_LIMITS.put(mapName.toLowerCase(Locale.ROOT).trim(), y);
        return true;
    }

    public static Map<String, Integer> getLearnedLimits() {
        return Collections.unmodifiableMap(LEARNED_LIMITS);
    }

    public static Map<String, Integer> getKnownLimits() {
        return Collections.unmodifiableMap(KNOWN_LIMITS);
    }

    public static void resetForTest() {
        LEARNED_LIMITS.clear();
    }
}
