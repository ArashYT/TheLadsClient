// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_1.embedded.cushions;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizedCushionsClient {
    public static final Logger LOGGER = LoggerFactory.getLogger("optimizedcushionsbackport");

    public static boolean isSodiumLoaded() {
        return FabricLoader.getInstance().isModLoaded("sodium");
    }

    /** Called from EmbeddedMods.clientInit() (was the client entrypoint). */
    public static void init() {
        if (isSodiumLoaded()) {
            LOGGER.info("Sodium detected - baking cushions into Sodium chunk meshes.");
        }

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof OptCushion) {
                CushionTracker.onLoad(entity);
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof OptCushion) {
                CushionTracker.onUnload(entity);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(CushionTracker::tick);
    }
}
