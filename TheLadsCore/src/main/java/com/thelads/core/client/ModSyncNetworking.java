package com.thelads.core.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class ModSyncNetworking {

    public static final CustomPacketPayload.Type<ModSyncPayload> HANDSHAKE_TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("thelads", "handshake"));

    public record ModSyncPayload(String data) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, ModSyncPayload> CODEC = CustomPacketPayload.codec(ModSyncPayload::write, ModSyncPayload::read);

        public static ModSyncPayload read(FriendlyByteBuf buf) {
            int readableBytes = buf.readableBytes();
            if (readableBytes > 0) {
                return new ModSyncPayload(buf.readUtf(readableBytes));
            } else {
                return new ModSyncPayload("");
            }
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.data);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return HANDSHAKE_TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(HANDSHAKE_TYPE, ModSyncPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(HANDSHAKE_TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().getConnection() != null) {
                    context.client().getConnection().getConnection().disconnect(Component.literal("Mod mismatch detected. Launching Mod Sync..."));
                }
                
                try {
                    Path syncFile = context.client().gameDirectory.toPath().resolve("mod_sync_request.json");
                    Files.writeString(syncFile, "{\"sync_required\": true}");
                } catch (IOException e) {
                    TheLadsCoreClient.LOGGER.error("Failed to write mod_sync_request.json", e);
                }
            });
        });
    }
}
