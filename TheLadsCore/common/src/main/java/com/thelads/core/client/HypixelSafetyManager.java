package com.thelads.core.client;

import com.thelads.core.client.bridge.LadsGameBridge;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Enforces server-side fair-play and tournament rules.
 * Automatically disables blacklisted modules when connected to Hypixel (hypixel.net / hypixel.io).
 */
public final class HypixelSafetyManager {
    private static final Set<String> DISALLOWED_MODULES = new HashSet<>();

    static {
        // Hypixel officially disallows:
        // - Minimaps with player radar, mob radar, or cave mapping (Xaero/VoxelMap).
        // - Automated reconnections bypassing AFK/lobby kicks (AutoReconnect).
        // - Automated inventory manipulation & fast packet bursts (MouseTweaks).
        // - Combat hit distance / reach measurement HUDs (ReachDisplay).
        // - Tile entity, container, and NBT data inspection HUDs (Jade).
        // - In-world container item previews and ESP-like badges (ShulkerBoxUtils).
        DISALLOWED_MODULES.add("Minimap");
        DISALLOWED_MODULES.add("XaeroMinimap");
        DISALLOWED_MODULES.add("XaeroWorldMap");
        DISALLOWED_MODULES.add("AutoReconnect");
        DISALLOWED_MODULES.add("MouseTweaks");
        DISALLOWED_MODULES.add("ReachDisplay");
        DISALLOWED_MODULES.add("Jade");
        DISALLOWED_MODULES.add("ShulkerBoxUtils");
    }

    private HypixelSafetyManager() {}

    /**
     * Checks if the currently active game connection is on Hypixel.
     */
    public static boolean isHypixel() {
        return isHypixelAddress(LadsGameBridge.get().getServerAddress());
    }

    /**
     * Determines whether a given server IP/hostname belongs to Hypixel.
     */
    public static boolean isHypixelAddress(String addr) {
        if (addr == null || addr.isEmpty() || addr.equalsIgnoreCase("Singleplayer")) {
            return false;
        }
        String clean = addr.trim().toLowerCase(Locale.ROOT);
        int colon = clean.indexOf(':');
        if (colon != -1) {
            clean = clean.substring(0, colon);
        }
        while (clean.endsWith(".")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.equals("hypixel.net") || clean.endsWith(".hypixel.net")
            || clean.equals("hypixel.io") || clean.endsWith(".hypixel.io");
    }

    /**
     * Checks if a specific module is disallowed on Hypixel.
     */
    public static boolean isDisallowedModule(String moduleName) {
        if (moduleName == null) return false;
        for (String disallowed : DISALLOWED_MODULES) {
            if (disallowed.equalsIgnoreCase(moduleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a module is currently blocked because the player is connected to Hypixel.
     */
    public static boolean isDisallowed(String moduleName) {
        return isHypixel() && isDisallowedModule(moduleName);
    }

    /**
     * Returns the set of modules blocked on Hypixel.
     */
    public static Set<String> getDisallowedModules() {
        return Collections.unmodifiableSet(DISALLOWED_MODULES);
    }

    public static String getBlockedNotice(String moduleName) {
        return moduleName + " is automatically disabled on Hypixel to comply with server rules.";
    }
}
