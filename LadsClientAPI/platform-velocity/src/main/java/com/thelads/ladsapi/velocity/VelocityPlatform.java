package com.thelads.ladsapi.velocity;

import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Velocity implementation of LadsPlatform.
 */
public final class VelocityPlatform implements LadsPlatform {
    private final Object plugin;
    private final ProxyServer server;
    private final Path dataFolder;
    private final Logger julLogger = Logger.getLogger("LadsClientAPI");

    public VelocityPlatform(Object plugin, ProxyServer server, Path dataFolder) {
        this.plugin = plugin;
        this.server = server;
        this.dataFolder = dataFolder;
    }

    @Override
    public String id() {
        return "velocity";
    }

    @Override
    public boolean isProxy() {
        return true;
    }

    @Override
    public Path dataFolder() {
        return dataFolder;
    }

    @Override
    public Logger logger() {
        return julLogger;
    }

    @Override
    public void runLater(Runnable task, int ticks) {
        server.getScheduler()
                .buildTask(plugin, task)
                .delay(Math.max(1, ticks) * 50L, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Override
    public Collection<? extends LadsPlayerHandle> online() {
        List<LadsPlayerHandle> list = new ArrayList<>();
        for (Player p : server.getAllPlayers()) {
            list.add(new VelocityPlayerHandle(p));
        }
        return list;
    }

    public VelocityPlayerHandle handleOf(Player player) {
        return new VelocityPlayerHandle(player);
    }
}
