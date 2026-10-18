package com.thelads.smp.features.arise.nb5.client;

import com.thelads.smp.features.arise.nb5.network.ParkourSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side Fabric entrypoint for NB5: initializes keybindings, client controllers,
 * and incoming network synchronization receivers.
 */
public final class AriseNB5Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB5Client");

    @Override
    public void onInitializeClient() {
        ParkourKeyMappings.init();

        // Register incoming stamina & action synchronization
        ClientPlayNetworking.registerGlobalReceiver(ParkourSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> ParkourClientController.onSync(payload));
        });

        LOGGER.info("[Lads SMP] Batch NB5 Client initialized (Parkour Keybindings, Animations & Stamina HUD)");
    }
}
