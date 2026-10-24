package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/**
 * Paper implementation of LadsPlayerHandle.
 */
public final class PaperPlayerHandle implements LadsPlayerHandle {
    private final Plugin plugin;
    private final Player player;

    public PaperPlayerHandle(Plugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    @Override
    public UUID uuid() {
        return player.getUniqueId();
    }

    @Override
    public String name() {
        return player.getName();
    }

    @Override
    public String world() {
        return player.getWorld().getName();
    }

    @Override
    public boolean hasPermission(String node) {
        return player.hasPermission(node);
    }

    @Override
    public void send(byte[] payload) {
        // Paper drops sendPluginMessage if channel not registered by client.
        // Public CraftPlayer.channels() returns live set; reflectively add lads:api before sending.
        try {
            Method channelsMethod = player.getClass().getMethod("channels");
            Object setObj = channelsMethod.invoke(player);
            if (setObj instanceof Set<?> rawSet) {
                @SuppressWarnings("unchecked")
                Set<String> channels = (Set<String>) rawSet;
                channels.add(LadsCore.CHANNEL);
            }
        } catch (Throwable ignored) {
        }
        player.sendPluginMessage(plugin, LadsCore.CHANNEL, payload);
    }

    @Override
    public void kick(String message) {
        player.kickPlayer(message);
    }

    public Player getPlayer() {
        return player;
    }
}
