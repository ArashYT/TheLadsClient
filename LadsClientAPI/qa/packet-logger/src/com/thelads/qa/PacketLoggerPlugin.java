package com.thelads.qa;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Instant;

/**
 * QA Verification Plugin for the "plain" Paper backend server.
 * Listens on channel "lads:api" to verify that incoming client traffic adheres
 * strictly to the anti-ban rule (zero lads:api packets sent by clean Lads Client).
 */
public final class PacketLoggerPlugin extends JavaPlugin implements PluginMessageListener {
    private static final String CHANNEL = "lads:api";
    private File logFile;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        logFile = new File(getDataFolder(), "packets.log");
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getLogger().info("[PacketLogger] Listening on incoming channel: " + CHANNEL);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        String entry = "[" + Instant.now() + "] UNEXPECTED_PACKET channel=" + channel
                + " player=" + player.getName()
                + " bytes=" + (message != null ? message.length : 0);
        getLogger().severe("[PacketLogger] " + entry);
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(entry);
        } catch (Exception e) {
            getLogger().warning("Failed to write to packets.log: " + e.getMessage());
        }
    }
}
