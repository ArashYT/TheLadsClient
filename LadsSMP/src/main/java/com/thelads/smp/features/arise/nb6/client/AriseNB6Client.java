package com.thelads.smp.features.arise.nb6.client;

import com.thelads.smp.features.arise.nb6.FishingJournalItem;
import com.thelads.smp.features.arise.nb6.network.StartFishingMinigamePayload;
import com.thelads.smp.features.arise.nb6.network.SyncFishingJournalPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Native Batch NB6 (Tide-style Fishing Overhaul).
 * Registers minigame HUD renderer, physics ticker, network receivers, and journal GUI.
 */
public final class AriseNB6Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB6Client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Arise NB6] Initializing fishing overhaul client systems...");

        // Connect FishingJournalItem to open screen on client
        FishingJournalItem.setClientScreenOpener(() -> {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.gui.setScreen(new FishingJournalScreen());
            }
        });

        // Register S2C Payloads
        ClientPlayNetworking.registerGlobalReceiver(
                StartFishingMinigamePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    FishingMinigameClient.start(
                            payload.hookId(),
                            payload.fishId(),
                            payload.targetCenter(),
                            payload.targetWidth(),
                            payload.difficulty(),
                            payload.maxTicks()
                    );
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SyncFishingJournalPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    FishingJournalScreen.updateClientCaught(payload.caughtFish());
                })
        );

        // Client Tick: simulate minigame physics and check player controls
        ClientTickEvents.END_CLIENT_TICK.register(FishingMinigameClient::tick);

        // HUD Render: draw minigame meter and cast charge bar using modern HudElementRegistry
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("ladssmp", "fishing_minigame_hud"),
                FishingMinigameHud::render
        );

        LOGGER.info("[Arise NB6] Fishing overhaul client systems initialized successfully.");
    }
}
