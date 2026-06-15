/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
 *  net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 */
package com.thelads.core.client;

import com.thelads.core.client.TheLadsCoreClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

@Environment(value=EnvType.CLIENT)
public class ModSyncNetworking {
    public static final CustomPacketPayload.Type<ModSyncPayload> HANDSHAKE_TYPE = new CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("thelads", "handshake"));

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(HANDSHAKE_TYPE, ModSyncPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(HANDSHAKE_TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().getConnection() != null) {
                context.client().getConnection().getConnection().disconnect((Component)Component.literal((String)"Mod mismatch detected. Launching Mod Sync..."));
            }
            try {
                Path syncFile = context.client().gameDirectory.toPath().resolve("mod_sync_request.json");
                Files.writeString(syncFile, (CharSequence)"{\"sync_required\": true}", new OpenOption[0]);
            }
            catch (IOException e) {
                TheLadsCoreClient.LOGGER.error("Failed to write mod_sync_request.json", (Throwable)e);
            }
        }));
    }

    public record ModSyncPayload(String data) implements CustomPacketPayload
    {
        public static final StreamCodec<FriendlyByteBuf, ModSyncPayload> CODEC = CustomPacketPayload.codec(ModSyncPayload::write, ModSyncPayload::read);

        public static ModSyncPayload read(FriendlyByteBuf buf) {
            int readableBytes = buf.readableBytes();
            if (readableBytes > 0) {
                return new ModSyncPayload(buf.readUtf(readableBytes));
            }
            return new ModSyncPayload("");
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.data);
        }

        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return HANDSHAKE_TYPE;
        }
    }
}

