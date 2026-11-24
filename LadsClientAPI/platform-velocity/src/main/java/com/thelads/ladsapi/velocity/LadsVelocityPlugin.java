package com.thelads.ladsapi.velocity;

import com.google.inject.Inject;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.velocity.event.LadsPlayerReadyEvent;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Main plugin class for Velocity proxies.
 */
@Plugin(
        id = "ladsclientapi",
        name = "LadsClientAPI",
        version = "1.6.0",
        description = "Lads Client integration for Velocity",
        authors = {"The Lads"}
)
public final class LadsVelocityPlugin {
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(LadsCore.CHANNEL);
    private static final byte[] RESET = "{\"t\":\"reset\"}".getBytes(StandardCharsets.UTF_8);

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataFolder;

    private VelocityPlatform platform;
    private LadsCore core;

    @Inject
    public LadsVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataFolder) {
        this.server = server;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.platform = new VelocityPlatform(this, server, dataFolder);
        this.core = LadsCore.start(platform);
        if (core == null) {
            logger.warn("LadsCore was already initialized. Staying disabled.");
            return;
        }

        // Register lads:api with ChannelRegistrar ONLY when the proxy rules for some backend (proxy-rules or proxy-rules-servers).
        // Otherwise, backend messages pass through untouched! Even registered, a message goes on to the backend unless the player
        // stands on a backend the proxy rules for (onPluginMessage).
        if (core.config().isProxyActive()) {
            server.getChannelRegistrar().register(CHANNEL);
            core.sessionManager().addReadyListener(ladsPlayer -> {
                server.getPlayer(ladsPlayer.uuid()).ifPresent(p -> {
                    server.getEventManager().fire(new LadsPlayerReadyEvent(p, ladsPlayer));
                });
            });
            logger.info("Proxy rules enabled for "
                    + (core.config().isProxyRules() ? "every backend" : "backends " + core.config().getProxyRulesServers())
                    + ": lads:api registered on proxy.");
        } else {
            logger.info("Backend forward mode: lads:api not registered on proxy (forwarded to backend).");
        }

        // Register /ladsapi command
        CommandMeta meta = server.getCommandManager().metaBuilder("ladsapi").plugin(this).build();
        server.getCommandManager().register(meta, new VelocityCommandHandler(core, server));

        logger.info("LadsClientAPI enabled on Velocity.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (core != null) {
            core.stop();
        }
        logger.info("LadsClientAPI disabled on Velocity.");
    }

    /**
     * Velocity waits for this event before it forwards the new backend's JoinGame and resumes reading from the backend, so the reset
     * always reaches the client before anything the new backend sends (vanilla clients ignore it). ServerPostConnectEvent is async
     * and can run after the backend's own hello, which would wipe the policy the client just accepted.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (core != null && core.config().isEnabled() && event.getPreviousServer().isPresent()) {
            event.getPlayer().sendPluginMessage(CHANNEL, RESET);
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        // Session/policy logic on the proxy only for backends it rules (proxy-rules, proxy-rules-servers). The client reset on the
        // switch: a ruled backend gets hello -> policy, any other drops the proxy's session so its own plugin takes over.
        if (core != null && core.config().isProxyActive()) {
            core.proxyServerChanged(platform.handleOf(event.getPlayer()));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (core != null && core.config().isProxyActive()) {
            core.quit(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (core != null && core.config().isProxyActive() && CHANNEL.equals(event.getIdentifier())) {
            if (event.getSource() instanceof Player player) {
                VelocityPlayerHandle handle = platform.handleOf(player);
                // A backend the proxy does not rule keeps its own plugin: the message goes on untouched.
                if (!core.config().proxyRulesFor(handle.world())) return;
                event.setResult(PluginMessageEvent.ForwardResult.handled());
                core.receive(handle, event.getData());
            }
        }
    }

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public Path getDataFolder() { return dataFolder; }
    public VelocityPlatform getPlatform() { return platform; }
    public LadsCore getCore() { return core; }
}
