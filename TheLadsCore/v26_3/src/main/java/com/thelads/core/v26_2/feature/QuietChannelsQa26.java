package com.thelads.core.v26_2.feature;

import com.thelads.core.client.qa.QuietChannelsQa;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.network.ClientHandshakePacket;
import snownee.jade.network.RequestBlockPacket;
import snownee.jade.network.RequestEntityPacket;

/**
 * QA only (the auto-world run, sampled at READY and by the Hypixel and AppleSkin captures): the 26.3 side of {@link QuietChannelsQa}. A sample
 * lists what this client announces (its play and configuration receivers, which Fabric sends as c:register / minecraft:register)
 * and what the integrated server on the other end of the connection was told (its sendable channels), and checks that no
 * hypixel:*, appleskin:* or jade:* channel is in either before the server speaks it.
 * <p>
 * The integrated server runs the embedded Jade, so it registers jade:client_handshake and Jade's channels are rightly announced.
 * LADS_QA_NOJADE=1 makes it a server without Jade before the world opens (its three Jade receivers unregistered): the join must
 * announce no jade:* channel and send no handshake; a moment after the first sample the server "installs Jade" (the receivers
 * come back), which has to make the client announce Jade's channels and complete the handshake.
 */
public final class QuietChannelsQa26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final boolean NO_JADE = "1".equals(System.getenv("LADS_QA_NOJADE"));
    private static final Identifier JADE_HANDSHAKE = ClientHandshakePacket.TYPE.id(), JADE_SERVER_HANDSHAKE = Identifier.parse("jade:server_handshake");
    private static final Map<CustomPacketPayload.Type<?>, ServerPlayNetworking.PlayPayloadHandler<?>> JADE_SERVER = new LinkedHashMap<>();
    private static int stage;
    private static long due;

    private static boolean pinged;

    private QuietChannelsQa26() {}

    /** The client's announced channels and the integrated server's list for this connection, checked; null: no connection yet. */
    public static Boolean sample(String when, boolean pingedNow) {
        if (pingedNow) pinged = true; // the first ping went out on this connection: hypixel:ping stays announced after it
        Minecraft mc = Minecraft.getInstance();
        try {
            var server = mc.getSingleplayerServer();
            if (mc.getConnection() == null || server == null || server.getPlayerList().getPlayers().isEmpty()) return null;
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            Set<String> announced = new TreeSet<>(), heard = new TreeSet<>();
            for (Identifier id : ClientPlayNetworking.getReceived()) announced.add(id.toString());
            for (Identifier id : ClientPlayNetworking.getGlobalReceivers()) announced.add(id.toString());
            for (Identifier id : ClientConfigurationNetworking.getGlobalReceivers()) announced.add(id.toString());
            for (Identifier id : ServerPlayNetworking.getSendable(player)) heard.add(id.toString());
            boolean serverJade = ServerPlayNetworking.getReceived(player).contains(JADE_HANDSHAKE);
            List<String> must = new ArrayList<>();
            if (pinged) must.add("hypixel:ping");
            if (serverJade) must.add(JADE_SERVER_HANDSHAKE.toString());
            boolean connected = WailaClientRegistration.instance().isServerConnected();
            LOGGER.info("Lads quiet channels {}: Jade handshake completed={}", when, connected);
            boolean ok = QuietChannelsQa.check(when, announced, heard, serverJade, pinged, must);
            if (serverJade != connected) {
                LOGGER.error("Lads quiet channels {}: FAIL Jade handshake completed={} but the server registered jade:client_handshake={}", when, connected, serverJade);
                return false;
            }
            return ok;
        } catch (RuntimeException failure) {
            LOGGER.error("Lads quiet channels {}: FAIL could not read the channel lists", when, failure);
            return false;
        }
    }

    /** Every tick of the auto-world run: the sample at READY, and with LADS_QA_NOJADE the server without Jade and its late Jade. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void tick(boolean ready) {
        if (stage >= 4) return;
        long now = System.nanoTime();
        if (stage == 0) {
            if (NO_JADE) {
                for (var type : List.<CustomPacketPayload.Type<?>>of(ClientHandshakePacket.TYPE, RequestBlockPacket.TYPE, RequestEntityPacket.TYPE)) {
                    var handler = ServerPlayNetworking.unregisterGlobalReceiver(type.id());
                    if (handler != null) JADE_SERVER.put(type, handler);
                }
                LOGGER.info("Lads quiet channels: the QA server has no Jade ({} server receivers removed before the world opens)", JADE_SERVER.size());
            }
            stage = 1;
        }
        if (stage == 1 && ready && sample("ready", false) != null) {
            if (NO_JADE) { stage = 2; due = now + 1_500_000_000L; } else stage = 4;
        }
        if (stage == 2 && now >= due) {
            JADE_SERVER.forEach((type, handler) -> ServerPlayNetworking.registerGlobalReceiver((CustomPacketPayload.Type) type, (ServerPlayNetworking.PlayPayloadHandler) handler));
            LOGGER.info("Lads quiet channels: the QA server installs Jade (jade:client_handshake and the request receivers registered)");
            stage = 3;
            due = now + 2_000_000_000L;
        }
        if (stage == 3 && now >= due && sample("jade installed on the server later", false) != null) stage = 4;
    }
}
