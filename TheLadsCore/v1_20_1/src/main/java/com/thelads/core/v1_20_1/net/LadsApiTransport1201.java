package com.thelads.core.v1_20_1.net;

import com.thelads.core.client.ladsapi.LadsApi;
import com.thelads.core.client.ladsapi.LadsApiClient;
import io.netty.buffer.Unpooled;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LadsClientAPI transport for Minecraft 1.20.1 (Fabric & Forge via Sinytra Connector).
 *
 * Anti-ban rule:
 * 1. The client NEVER announces lads:api (no Fabric global receiver registration, no FML REGISTER).
 * 2. The client NEVER initiates communication; outgoing packets are sent strictly in response to server payloads
 *    as directed by LadsApiClient.
 * 3. Outgoing payloads use vanilla ServerboundCustomPayloadPacket on the current connection.
 */
public final class LadsApiTransport1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    public static final ResourceLocation CHANNEL = new ResourceLocation(LadsApi.NAMESPACE, LadsApi.PATH);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private LadsApiTransport1201() {}

    /**
     * Installs the 1.20.1 transport into LadsApiClient and hooks disconnect reset.
     * Safe to call multiple times; installation is performed exactly once.
     */
    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        LadsApiClient.install(LadsApiTransport1201::send, "1.20.1");
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LadsApiClient.reset());
        LOGGER.debug("LadsApi 1.20.1 transport initialized.");
    }

    /**
     * Sends one lads:api payload via a vanilla ServerboundCustomPayloadPacket on the active connection.
     * Called on the game thread by LadsApiClient.
     */
    public static void send(byte[] payload) {
        if (!isValidPayload(payload)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return;
        }
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            connection.send(new ServerboundCustomPayloadPacket(CHANNEL, buf));
        } catch (RuntimeException failure) {
            LOGGER.debug("LadsApi: could not send custom payload: {}", failure.toString());
        }
    }

    /**
     * Validates that an outbound payload is non-null, non-empty, and does not exceed MAX_OUT.
     */
    public static boolean isValidPayload(byte[] payload) {
        return payload != null && payload.length > 0 && payload.length <= LadsApi.MAX_OUT;
    }

    /**
     * Validates that an inbound payload length is non-negative and does not exceed MAX_IN.
     */
    public static boolean isValidInboundLength(int length) {
        return length >= 0 && length <= LadsApi.MAX_IN;
    }

    /**
     * Checks if the given ResourceLocation matches lads:api.
     */
    public static boolean isLadsApiChannel(ResourceLocation id) {
        return id != null && LadsApi.NAMESPACE.equals(id.getNamespace()) && LadsApi.PATH.equals(id.getPath());
    }
}
