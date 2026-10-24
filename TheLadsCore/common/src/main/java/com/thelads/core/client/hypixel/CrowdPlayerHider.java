package com.thelads.core.client.hypixel;

import java.util.ArrayList;
import java.util.List;

/**
 * CrowdPlayerHider: Hides player entity models in crowded hubs and spawn zones to eliminate draw call lag.
 * - Pit Lag Reducer: Hides spawn players in The Pit while engaged in combat.
 * - SkyBlock Player Hiding: Hides player entity models around NPCs and hub areas in Hypixel SkyBlock.
 */
public final class CrowdPlayerHider {
    public static final String PIT_LAG = "Pit Lag Reducer";
    public static final String SKYBLOCK_NPC_HIDE = "SkyBlock Player Hiding";

    private record NpcPos(double x, double y, double z) {}
    private static final List<NpcPos> KNOWN_NPCS = new ArrayList<>();

    private CrowdPlayerHider() {}

    public static boolean isPitLagEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(PIT_LAG);
    }

    public static boolean isSkyBlockHideEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(SKYBLOCK_NPC_HIDE);
    }

    public static void registerNpc(double x, double y, double z) {
        if (KNOWN_NPCS.size() > 100) KNOWN_NPCS.clear();
        for (NpcPos p : KNOWN_NPCS) {
            double dx = p.x - x, dy = p.y - y, dz = p.z - z;
            if (dx * dx + dy * dy + dz * dz < 4.0) return;
        }
        KNOWN_NPCS.add(new NpcPos(x, y, z));
    }

    public static void clearNpcs() {
        KNOWN_NPCS.clear();
    }

    /**
     * Determines whether a player entity should be hidden from rendering.
     * Allocation-free hot path called per entity per frame.
     */
    public static boolean shouldHidePlayer(boolean isLocalPlayer, double x, double y, double z) {
        if (isLocalPlayer) return false;
        if (!HypixelMods.isActiveOnCurrentServer()) return false;

        String[] loc = HypixelMods.location();
        String gametype = loc != null ? loc[0] : "";

        // 1. Pit Lag Reducer
        if (isPitLagEnabled() && ("PIT".equalsIgnoreCase(gametype) || (loc != null && loc[1] != null && loc[1].toLowerCase().contains("pit")))) {
            // Pit spawn platform: players at spawn height (Y >= 110) or within central safe zone
            if (y >= 110.0 || (Math.abs(x) < 25.0 && Math.abs(z) < 25.0 && y >= 95.0)) {
                return true;
            }
        }

        // 2. SkyBlock Player Hiding
        if (isSkyBlockHideEnabled() && "SKYBLOCK".equalsIgnoreCase(gametype)) {
            double radius = HypixelMods.skyblockNpcRadius();
            double radiusSq = radius * radius;
            for (int i = 0; i < KNOWN_NPCS.size(); i++) {
                NpcPos npc = KNOWN_NPCS.get(i);
                double dx = npc.x - x, dy = npc.y - y, dz = npc.z - z;
                if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                    return true;
                }
            }
        }

        return false;
    }
}
