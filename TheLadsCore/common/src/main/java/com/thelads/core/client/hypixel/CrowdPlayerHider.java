package com.thelads.core.client.hypixel;

/**
 * CrowdPlayerHider: Hides player entity models in The Pit spawn zone to eliminate draw call lag.
 */
public final class CrowdPlayerHider {
    public static final String PIT_LAG = "Pit Lag Reducer";

    private CrowdPlayerHider() {}

    public static boolean isPitLagEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(PIT_LAG);
    }

    /**
     * Determines whether a player entity in The Pit spawn should be hidden from rendering.
     * Allocation-free hot path called per entity per frame.
     */
    public static boolean shouldHidePitPlayer(boolean isLocalPlayer, double x, double y, double z) {
        if (isLocalPlayer) return false;
        if (!isPitLagEnabled()) return false;

        String[] loc = HypixelMods.location();
        String gametype = loc != null ? loc[0] : "";

        // Pit Lag Reducer: check if currently in Pit
        if ("PIT".equalsIgnoreCase(gametype) || (loc != null && loc[1] != null && loc[1].toLowerCase().contains("pit"))) {
            // Pit spawn platform: players at spawn height (Y >= 110) or within central safe zone
            if (y >= 110.0 || (Math.abs(x) < 25.0 && Math.abs(z) < 25.0 && y >= 95.0)) {
                return true;
            }
        }
        return false;
    }
}
