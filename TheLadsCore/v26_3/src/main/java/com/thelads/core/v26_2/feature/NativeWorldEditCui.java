package com.thelads.core.v26_2.feature;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.worldedit.WorldEditCUI;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;

/**
 * WorldEdit CUI networking for 26.3.
 * Registers clientbound and serverbound payload types for worldedit:cui without registering a global receiver,
 * announces the channel per connection on join, and sends the v|4 handshake.
 */
public final class NativeWorldEditCui {
    private static boolean initialized = false;
    private static final ClientPlayNetworking.PlayPayloadHandler<WorldEditCuiPayload> RECEIVER = (payload, context) -> {
        Minecraft mc = context.client();
        if (mc != null && mc.isSameThread()) {
            WorldEditCUI.onPacket("worldedit:cui", payload.data());
        } else if (mc != null) {
            mc.execute(() -> WorldEditCUI.onPacket("worldedit:cui", payload.data()));
        }
    };

    private NativeWorldEditCui() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            PayloadTypeRegistry.clientboundPlay().register(WorldEditCuiPayload.TYPE, WorldEditCuiPayload.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(WorldEditCuiPayload.TYPE, WorldEditCuiPayload.CODEC);
        } catch (RuntimeException ignored) {}

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WorldEditCUI.clear());
    }

    public static void onJoin() {
        WorldEditCUI.clear();
        if (!WorldEditCUI.on || HypixelSafetyManager.isDisallowed(WorldEditCUI.NAME)) return;
        try {
            if (!ClientPlayNetworking.getReceived().contains(WorldEditCuiPayload.TYPE.id())) {
                ClientPlayNetworking.registerReceiver(WorldEditCuiPayload.TYPE, RECEIVER);
            }
            ClientPlayNetworking.send(new WorldEditCuiPayload(WorldEditCUI.getHandshakePayload()));
        } catch (RuntimeException ignored) {}
    }
}
