// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.decoration.Cushion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptimizedCushionsClient {
    public static final Logger LOGGER = LoggerFactory.getLogger("optimizedcushions");

    public static boolean isObeLoaded() {
        return FabricLoader.getInstance().isModLoaded("obe");
    }

    public static boolean isSodiumLoaded() {
        return FabricLoader.getInstance().isModLoaded("sodium");
    }

    /** Called from EmbeddedMods.clientInit() (was the client entrypoint). */
    public static void init() {
        if (isObeLoaded()) {
            LOGGER.info("Optimised Block Entities detected - Optimized Cushions client is disabled, cushions render as vanilla entities.");
            return;
        }

        if (isSodiumLoaded()) {
            LOGGER.info("Sodium detected - baking cushions into Sodium chunk meshes.");
        }

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Cushion cushion) {
                CushionTracker.onLoad(cushion);
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Cushion cushion) {
                CushionTracker.onUnload(cushion);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(CushionTracker::tick);
    }
}
