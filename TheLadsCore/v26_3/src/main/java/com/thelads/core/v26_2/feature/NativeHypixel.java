package com.thelads.core.v26_2.feature;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.config.ModuleSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
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

/**
 * The Hypixel modules and the real ping (common HypixelMods, RealPing) on 26.3: server chat through Fabric's game-message event
 * (Chat Cleanup cancels it), a client tick, and the Mod API ping on the hypixel:ping channel as a payload that keeps its bytes and
 * the time it was decoded on the network thread. With the official Hypixel Mod API mod installed the channel is its own: the ping
 * then uses status pings. The channel is announced (minecraft:register) only on the connection about to send the first ping, and
 * RealPing pings Hypixel hosts only: no other server sees it.
 */
public final class NativeHypixel {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    static final CustomPacketPayload.Type<PingPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.parse(RealPing.CHANNEL));
    private static boolean channel, answersQa;
    private static final ClientPlayNetworking.PlayPayloadHandler<PingPayload> RECEIVER = (payload, context) ->
        RealPing.receive(RealPing.CHANNEL, payload.data(), payload.nanos() != 0 ? payload.nanos() : System.nanoTime());

    /** A hypixel:ping payload's bytes, and when they were decoded (0: not decoded, e.g. the integrated server's own object). */
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
        ModuleSupport.registerBuiltIn(HypixelMods.HYPIXEL_MODS, "Session Stats", "Bed Wars Height Limit");
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !HypixelMods.chat(message.getString(), System.nanoTime()));
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (!overlay && HypixelMods.isStreamerMode()) {
                String raw = message.getString();
                String masked = HypixelMods.filterIncomingChat(raw);
                if (!masked.equals(raw)) return net.minecraft.network.chat.Component.literal(masked);
            }
            return message;
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !HypixelMods.handleOutgoingCommand(command));
        ClientSendMessageEvents.MODIFY_CHAT.register(HypixelMods::handleOutgoingChat);
        ClientSendMessageEvents.MODIFY_COMMAND.register(HypixelMods::handleOutgoingChat);
        ClientTickEvents.END_CLIENT_TICK.register(client -> HypixelMods.tick(LadsGameBridge.get(), client.level, client.getConnection(), System.nanoTime()));
        if (FabricLoader.getInstance().isModLoaded("hypixel_mod_api")) return;
        try {
            PayloadTypeRegistry.clientboundPlay().register(TYPE, PingPayload.CODEC);
            PayloadTypeRegistry.serverboundPlay().register(TYPE, PingPayload.CODEC);
            channel = true;
        } catch (RuntimeException taken) {
            LOGGER.info("Lads real ping: {} is registered by another mod, using status pings", RealPing.CHANNEL);
        }
        // QA only (HypixelCapture): the integrated server answers like Hypixel ("pong"), so the QA world exercises the whole round trip.
        if (channel && "1".equals(System.getenv("LADS_VERIFY_CAPTURE_HYPIXEL")))
            answersQa = ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> // 37 ms later: told apart from singleplayer's 0 ms
                java.util.concurrent.CompletableFuture.delayedExecutor(37, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() ->
                    ServerPlayNetworking.send(context.player(), new PingPayload(new byte[] {1, 1, 4, 'p', 'o', 'n', 'g'}, 0))));
    }

    /** QA: the integrated server answers hypixel:ping this run. */
    static boolean answersQa() { return answersQa; }

    /** RealPing's Mod API request (the bridge's sendPluginMessage). */
    public static boolean send(String name, byte[] data) {
        if (!channel || !RealPing.CHANNEL.equals(name) || Minecraft.getInstance().getConnection() == null) return false;
        try {
            // This connection's receiver, registered (and announced) just before its first ping; gone with the connection.
            if (!ClientPlayNetworking.getReceived().contains(TYPE.id())) ClientPlayNetworking.registerReceiver(TYPE, RECEIVER);
            ClientPlayNetworking.send(new PingPayload(data, 0));
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("Lads real ping: could not send {}: {}", name, failure.toString());
            return false;
        }
    }
}
