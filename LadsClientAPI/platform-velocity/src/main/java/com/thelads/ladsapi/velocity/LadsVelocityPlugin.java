package com.thelads.ladsapi.velocity;

import com.google.inject.Inject;
import com.thelads.ladsapi.LadsCore;
import com.thelads.ladsapi.velocity.event.LadsPlayerReadyEvent;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
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
        version = "1.0.0",
        description = "Lads Client integration for Velocity",
        authors = {"The Lads"}
)
public final class LadsVelocityPlugin {
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(LadsCore.CHANNEL);

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

        // Register lads:api with ChannelRegistrar ONLY in proxy-rules mode
        // Otherwise, backend messages pass through untouched!
        if (core.config().isProxyRules()) {
            server.getChannelRegistrar().register(CHANNEL);
            core.sessionManager().addReadyListener(ladsPlayer -> {
                server.getPlayer(ladsPlayer.uuid()).ifPresent(p -> {
                    server.getEventManager().fire(new LadsPlayerReadyEvent(p, ladsPlayer));
                });
            });
            logger.info("Proxy rules enabled: lads:api registered on proxy.");
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

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        // Proxy mode: always send {"t":"reset"} on every ServerPostConnectEvent (vanilla clients ignore it)
        player.sendPluginMessage(CHANNEL, "{\"t\":\"reset\"}".getBytes(StandardCharsets.UTF_8));

        // Only run full session/policy logic on the proxy when proxy-rules: true
        if (core != null && core.config().isProxyRules()) {
            if (event.getPreviousServer() == null) {
                core.join(platform.handleOf(player));
            } else {
                core.worldChanged(platform.handleOf(player));
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (core != null && core.config().isProxyRules()) {
            core.quit(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (core != null && core.config().isProxyRules() && CHANNEL.equals(event.getIdentifier())) {
            if (event.getSource() instanceof Player player) {
                event.setResult(PluginMessageEvent.ForwardResult.handled());
                core.receive(platform.handleOf(player), event.getData());
            }
        }
    }

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public Path getDataFolder() { return dataFolder; }
    public VelocityPlatform getPlatform() { return platform; }
    public LadsCore getCore() { return core; }
}
