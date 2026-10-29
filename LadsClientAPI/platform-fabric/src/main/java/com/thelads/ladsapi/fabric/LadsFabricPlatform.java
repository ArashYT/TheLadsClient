package com.thelads.ladsapi.fabric;

import com.thelads.ladsapi.platform.LadsPlatform;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class LadsFabricPlatform implements LadsPlatform {
    private static final Logger LOGGER = Logger.getLogger("LadsClientAPI");
    private final Path dataFolder;
    private final Map<UUID, LadsFabricPlayerHandle> players = new ConcurrentHashMap<>();
    private volatile MinecraftServer server;

    private final com.thelads.ladsapi.util.TickScheduler scheduler = new com.thelads.ladsapi.util.TickScheduler(LOGGER);

    public LadsFabricPlatform() {
        this.dataFolder = FabricLoader.getInstance().getConfigDir().resolve("ladsclientapi");
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    private void onServerTick(MinecraftServer server) {
        this.server = server;
        scheduler.tick();
    }

    public LadsFabricPlayerHandle getOrCreatePlayer(ServerPlayer player) {
        return players.compute(player.getUUID(), (uuid, existing) -> new LadsFabricPlayerHandle(player));
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    @Override
    public String id() {
        return "fabric";
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    @Override
    public Path dataFolder() {
        return dataFolder;
    }

    @Override
    public Logger logger() {
        return LOGGER;
    }

    @Override
    public void runLater(Runnable task, int ticks) {
        if (ticks <= 0) {
            if (server != null) {
                server.execute(task);
            } else {
                task.run();
            }
        } else {
            scheduler.runLater(task, ticks);
        }
    }

    @Override
    public Collection<? extends LadsPlayerHandle> online() {
        return players.values();
    }
}
