package com.thelads.smp.features.arise.nb1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native clean-room implementation of Faster Portals.
 * Overrides survival Nether Portal waiting delay from vanilla 80 ticks down to a configurable value (default: 10 ticks).
 */
public final class FasterPortalsFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-FasterPortals");

    public static void init() {
        LOGGER.info("[Lads SMP] Faster Portals initialized (portalWaitTime={} ticks)", getPortalWaitTime());
    }

    public static boolean isEnabled() {
        return AriseNB1Config.get().fasterPortals.enabled;
    }

    public static int getPortalWaitTime() {
        return AriseNB1Config.get().fasterPortals.portalWaitTime;
    }
}
