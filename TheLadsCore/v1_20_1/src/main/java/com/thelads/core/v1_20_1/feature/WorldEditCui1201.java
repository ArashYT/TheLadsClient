package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.worldedit.WorldEditCUI;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * WorldEdit CUI networking for 1.20.1.
 * Announces the channel per-connection and sends the v|4 handshake only when enabled
 * and not disallowed on Hypixel, matching the Hypixel1201 per-connection model.
 */
public final class WorldEditCui1201 {
    private static final ResourceLocation CHANNEL = new ResourceLocation("worldedit", "cui");
    private static final ClientPlayNetworking.PlayChannelHandler RECEIVER = (client, handler, buf, sender) -> {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        client.execute(() -> WorldEditCUI.onPacket("worldedit:cui", data));
    };

    private WorldEditCui1201() {}

    public static void onJoin() {
        WorldEditCUI.clear();
        if (!WorldEditCUI.on || HypixelSafetyManager.isDisallowed(WorldEditCUI.NAME)) return;
        try {
            if (!ClientPlayNetworking.getReceived().contains(CHANNEL)) {
                ClientPlayNetworking.registerReceiver(CHANNEL, RECEIVER);
            }
            ClientPlayNetworking.send(CHANNEL, new FriendlyByteBuf(Unpooled.wrappedBuffer(WorldEditCUI.getHandshakePayload())));
        } catch (RuntimeException ignored) {}
    }
}
