package com.thelads.core.v26_2.feature;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.config.ModuleSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NativeHypixel {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    static final CustomPacketPayload.Type<PingPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.parse(RealPing.CHANNEL));
    private static boolean channel, answersQa;

    record PingPayload(byte[] data, long nanos) implements CustomPacketPayload {
        static final StreamCodec<FriendlyByteBuf, PingPayload> CODEC = StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new PingPayload(data, System.nanoTime());
        });
        @Override public Type<PingPayload> type() { return TYPE; }
    }

    private NativeHypixel() {}

    public static void initialize() {
        ModuleSupport.registerBuiltIn(HypixelMods.HYPIXEL_MODS);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !HypixelMods.chat(message.getString(), System.nanoTime()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> HypixelMods.tick(LadsGameBridge.get(), client.level, client.getConnection(), System.nanoTime()));
        if (FabricLoader.getInstance().isModLoaded("hypixel_mod_api")) return;
        try {
            PayloadTypeRegistry.clientboundPlay().register(TYPE, PingPayload.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(TYPE, PingPayload.CODEC);
            ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                RealPing.receive(RealPing.CHANNEL, payload.data(), payload.nanos() != 0 ? payload.nanos() : System.nanoTime()));
            channel = true;
        } catch (RuntimeException taken) {
            LOGGER.info("Lads real ping: {} is registered by another mod, using status pings", RealPing.CHANNEL);
        }
        if (channel && "1".equals(System.getenv("LADS_VERIFY_CAPTURE_HYPIXEL")))
            answersQa = ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                java.util.concurrent.CompletableFuture.delayedExecutor(37, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() ->
                    ServerPlayNetworking.send(context.player(), new PingPayload(new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, 0))));
    }

    static boolean answersQa() { return answersQa; }

    public static boolean send(String name, byte[] data) {
        if (!channel || !RealPing.CHANNEL.equals(name) || Minecraft.getInstance().getConnection() == null) return false;
        try {
            ClientPlayNetworking.send(new PingPayload(data, 0));
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("Lads real ping: could not send {}: {}", name, failure.toString());
            return false;
        }
    }
}
