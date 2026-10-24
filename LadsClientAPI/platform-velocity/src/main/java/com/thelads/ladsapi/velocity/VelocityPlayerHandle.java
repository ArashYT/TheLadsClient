package com.thelads.ladsapi.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Velocity implementation of LadsPlayerHandle.
 */
public final class VelocityPlayerHandle implements LadsPlayerHandle {
    private final Player player;

    public VelocityPlayerHandle(Player player) {
        this.player = player;
    }

    @Override
    public UUID uuid() {
        return player.getUniqueId();
    }

    @Override
    public String name() {
        return player.getUsername();
    }

    @Override
    public String world() {
        // On a proxy: world() = backend server name
        return player.getCurrentServer()
                .map(ServerConnection::getServer)
                .map(s -> s.getServerInfo().getName())
                .orElse("");
    }

    @Override
    public boolean hasPermission(String node) {
        return player.hasPermission(node);
    }

    @Override
    public void send(byte[] payload) {
        player.sendPluginMessage(LadsVelocityPlugin.CHANNEL, payload);
    }

    @Override
    public void kick(String message) {
        player.disconnect(Component.text(message));
    }

    public Player getPlayer() {
        return player;
    }
}
