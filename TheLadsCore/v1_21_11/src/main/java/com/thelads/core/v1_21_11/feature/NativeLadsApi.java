package com.thelads.core.v1_21_11.feature;

import com.thelads.core.client.ladsapi.LadsApiClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LadsClientAPI transport for 1.21.11.
 * Registers the lads:api payload codec so incoming and outgoing packets decode/encode properly,
 * installs the send transport into LadsApiClient, and wires up disconnect reset.
 * Does NOT register a global receiver (anti-ban rule: client never announces the channel in minecraft:register).
 */
public final class NativeLadsApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static boolean initialized;

    private NativeLadsApi() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            PayloadTypeRegistry.playS2C().register(LadsApiPayload.TYPE, LadsApiPayload.CODEC);
            PayloadTypeRegistry.playC2S().register(LadsApiPayload.TYPE, LadsApiPayload.CODEC);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to register lads:api payload type: {}", e.toString());
        }

        LadsApiClient.install(NativeLadsApi::send, "1.21.11");
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LadsApiClient.reset());
        LOGGER.info("LadsClientAPI transport initialized for 1.21.11");
    }

    public static void send(byte[] payload) {
        if (payload == null) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            LOGGER.debug("Cannot send lads:api payload: connection is null");
            return;
        }
        try {
            connection.send(new ServerboundCustomPayloadPacket(new LadsApiPayload(payload)));
        } catch (RuntimeException e) {
            LOGGER.debug("Failed to send lads:api payload: {}", e.toString());
        }
    }
}
