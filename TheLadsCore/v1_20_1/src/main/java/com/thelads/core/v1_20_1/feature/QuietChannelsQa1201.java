package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.qa.QuietChannelsQa;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only: the 1.20.1 side of {@link QuietChannelsQa}. A sample lists what this client announces (its receivers, which Fabric sends
 * as minecraft:register at join and when one is added) and what the integrated server on the other end of the connection was
 * told (its sendable channels), and checks that no hypixel:* or appleskin:* channel is in either before the server speaks it.
 * The script step "channels [name]" takes one (Hypixel1201 and the items probe take their own).
 */
public final class QuietChannelsQa1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    private static boolean pinged;

    private QuietChannelsQa1201() {}

    /** The client's announced channels and the integrated server's list for this connection, checked; null: no connection yet. */
    public static Boolean sample(String when, boolean pingedNow) {
        if (pingedNow) pinged = true; // the first ping went out on this connection: hypixel:ping stays announced after it
        Minecraft mc = Minecraft.getInstance();
        try {
            var server = mc.getSingleplayerServer();
            if (mc.getConnection() == null || server == null || server.getPlayerList().getPlayers().isEmpty()) return null;
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            Set<String> announced = new TreeSet<>(), heard = new TreeSet<>();
            for (ResourceLocation id : ClientPlayNetworking.getReceived()) announced.add(id.toString());
            for (ResourceLocation id : ClientPlayNetworking.getGlobalReceivers()) announced.add(id.toString());
            for (ResourceLocation id : ServerPlayNetworking.getSendable(player)) heard.add(id.toString());
            List<String> must = new ArrayList<>();
            if (pinged) must.add("hypixel:ping");
            return QuietChannelsQa.check(when, announced, heard, false, pinged, must);
        } catch (RuntimeException failure) {
            LOGGER.error("Lads quiet channels {}: FAIL could not read the channel lists", when, failure);
            return false;
        }
    }
}
