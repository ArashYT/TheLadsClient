package com.thelads.ladsapi.paper;

import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.paper.event.LadsPlayerReadyEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * Main plugin class for Paper and Spigot servers.
 */
public final class LadsPaperPlugin extends JavaPlugin implements PluginMessageListener {
    private PaperPlatform platform;
    private LadsCore core;

    @Override
    public void onEnable() {
        this.platform = new PaperPlatform(this);
        this.core = LadsCore.start(platform);
        if (core == null) {
            getLogger().warning("LadsCore was already initialized by another platform. Staying disabled.");
            return;
        }

        // Fire Bukkit LadsPlayerReadyEvent when a player finishes protocol handshake
        core.sessionManager().addReadyListener(ladsPlayer -> {
            Player p = Bukkit.getPlayer(ladsPlayer.uuid());
            if (p != null) {
                Bukkit.getPluginManager().callEvent(new LadsPlayerReadyEvent(p, ladsPlayer));
            }
        });

        // Register plugin messaging channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, LadsCore.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, LadsCore.CHANNEL, this);

        // Register events
        getServer().getPluginManager().registerEvents(new PaperEventListener(core, platform), this);

        // Register command and tab completer
        PluginCommand cmd = getCommand("ladsapi");
        if (cmd != null) {
            cmd.setExecutor(new PaperCommandHandler(core));
            cmd.setTabCompleter(new PaperTabCompleter(core));
        }

        // PlaceholderAPI expansion if present
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new LadsPlaceholderExpansion(core).register();
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        getLogger().info("LadsClientAPI enabled on Paper/Spigot.");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.stop();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getLogger().info("LadsClientAPI disabled.");
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        if (LadsCore.CHANNEL.equals(channel) && core != null) {
            core.receive(platform.handleOf(player), message);
        }
    }

    public LadsCore getCore() {
        return core;
    }

    public PaperPlatform getPlatform() {
        return platform;
    }
}
