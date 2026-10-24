package com.thelads.core.v1_8_9.perf;

import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.v1_8_9.feature.EntityCulling189;
import com.thelads.core.v1_8_9.feature.HudCache189;
import com.thelads.core.v1_8_9.feature.ScoreboardCache189;
import com.thelads.core.v1_8_9.feature.TabListCache189;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sarcio on 1.8.9: session memory-leak remediation and cache cleanup on world unload/disconnect,
 * eliminating retained entity/world references across sessions (Sarcio parity).
 */
public final class Sarcio189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    private Sarcio189() {}

    /**
     * Called when leaving a singleplayer world or disconnecting from a multiplayer server.
     * Purges retained world and player references from internal caches.
     */
    public static void onWorldUnload() {
        EntityCulling189.clear();
        BakedBlockEntities.clear();
        ScoreboardCache189.reset();
        TabListCache189.reset();
        HudCache189.free();
        LOGGER.info("Sarcio 1.8.9: purged session caches and world references on disconnect");
    }
}
