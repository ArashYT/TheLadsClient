package com.thelads.smp.features.arise.nb6.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Common network registration for Native Batch NB6 CustomPacketPayload types.
 */
public final class NB6Payloads {
    private NB6Payloads() {}

    public static void registerAll() {
        // S2C Packets
        PayloadTypeRegistry.clientboundPlay().register(
                StartFishingMinigamePayload.TYPE,
                StartFishingMinigamePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                SyncFishingJournalPayload.TYPE,
                SyncFishingJournalPayload.CODEC
        );

        // C2S Packets
        PayloadTypeRegistry.serverboundPlay().register(
                FinishFishingMinigamePayload.TYPE,
                FinishFishingMinigamePayload.CODEC
        );
    }
}
